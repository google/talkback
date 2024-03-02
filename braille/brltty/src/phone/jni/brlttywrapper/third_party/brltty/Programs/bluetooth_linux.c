/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#include "prologue.h"

#include <errno.h>
#include <sys/socket.h>
#include <bluetooth/bluetooth.h>
#include <bluetooth/hci.h>
#include <bluetooth/hci_lib.h>
#include <bluetooth/l2cap.h>
#include <bluetooth/sdp.h>
#include <bluetooth/sdp_lib.h>
#include <bluetooth/rfcomm.h>

#include "log.h"
#include "parameters.h"
#include "io_bluetooth.h"
#include "bluetooth_internal.h"
#include "async_handle.h"
#include "async_io.h"
#include "io_misc.h"
#include "timing.h"

struct BluetoothConnectionExtensionStruct {
  SocketDescriptor socketDescriptor;
  struct sockaddr_rc localAddress;
  struct sockaddr_rc remoteAddress;
  AsyncHandle inputMonitor;
};

typedef union {
  unsigned char hciEvent[HCI_MAX_EVENT_SIZE];

  struct {
    unsigned char type;

    union {
      struct {
        hci_event_hdr header;

        union {
          evt_remote_name_req_complete rn;
          evt_cmd_complete cc;
          evt_cmd_status cs;
        } data;
      } PACKED hciEvent;
    } data;
  } PACKED fields;
} BluetoothPacket;

static void
bthMakeAddress (bdaddr_t *address, uint64_t bda) {
  unsigned int index;

  for (index=0; index<BDA_SIZE; index+=1) {
    address->b[index] = bda & 0XFF;
    bda >>= 8;
  }
}

BluetoothConnectionExtension *
bthNewConnectionExtension (uint64_t bda) {
  BluetoothConnectionExtension *bcx;

  if ((bcx = malloc(sizeof(*bcx)))) {
    memset(bcx, 0, sizeof(*bcx));

    bcx->localAddress.rc_family = AF_BLUETOOTH;
    bcx->localAddress.rc_channel = 0;
    bacpy(&bcx->localAddress.rc_bdaddr, BDADDR_ANY); /* Any HCI. No support for explicit
                                                      * interface specification yet.
                                                      */

    bcx->remoteAddress.rc_family = AF_BLUETOOTH;
    bcx->remoteAddress.rc_channel = 0;
    bthMakeAddress(&bcx->remoteAddress.rc_bdaddr, bda);

    bcx->socketDescriptor = INVALID_SOCKET_DESCRIPTOR;
    return bcx;
  } else {
    logMallocError();
  }

  return NULL;
}

static void
bthCancelInputMonitor (BluetoothConnectionExtension *bcx) {
  if (bcx->inputMonitor) {
    asyncCancelRequest(bcx->inputMonitor);
    bcx->inputMonitor = NULL;
  }
}

void
bthReleaseConnectionExtension (BluetoothConnectionExtension *bcx) {
  bthCancelInputMonitor(bcx);
  closeSocket(&bcx->socketDescriptor);
  free(bcx);
}

static int
bthGetConnectLogLevel (int error) {
  switch (error) {
    case EHOSTUNREACH:
    case EHOSTDOWN:
      return LOG_CATEGORY(BLUETOOTH_IO);

    default:
      return LOG_ERR;
  }
}

int
bthOpenChannel (BluetoothConnectionExtension *bcx, uint8_t channel, int timeout) {
  bcx->remoteAddress.rc_channel = channel;

  if ((bcx->socketDescriptor = socket(PF_BLUETOOTH, SOCK_STREAM, BTPROTO_RFCOMM)) != -1) {
    setCloseOnExec(bcx->socketDescriptor, 1);

    if (bind(bcx->socketDescriptor, (struct sockaddr *)&bcx->localAddress, sizeof(bcx->localAddress)) != -1) {
      if (setBlockingIo(bcx->socketDescriptor, 0)) {
        int connectResult = LINUX_BLUETOOTH_CHANNEL_CONNECT_ASYNCHRONOUS?
                              connectSocket(bcx->socketDescriptor,
                                            (struct sockaddr *)&bcx->remoteAddress,
                                            sizeof(bcx->remoteAddress),
                                            timeout):
                              connect(bcx->socketDescriptor,
                                      (struct sockaddr *)&bcx->remoteAddress,
                                      sizeof(bcx->remoteAddress));

        if (connectResult != -1) return 1;
        logSystemProblem(bthGetConnectLogLevel(errno), "RFCOMM connect");
      }
    } else {
      logSystemError("RFCOMM bind");
    }

    setSocketNoLinger(bcx->socketDescriptor);
    close(bcx->socketDescriptor);
    bcx->socketDescriptor = INVALID_SOCKET_DESCRIPTOR;
  } else {
    logSystemError("RFCOMM socket");
  }

  return 0;
}

static int
bthFindChannel (uint8_t *channel, sdp_record_t *record) {
  int foundChannel = 0;
  int stopSearching = 0;
  sdp_list_t *protocolsList;

  if (!(sdp_get_access_protos(record, &protocolsList))) {
    sdp_list_t *protocolsElement = protocolsList;

    while (protocolsElement) {
      sdp_list_t *protocolList = (sdp_list_t *)protocolsElement->data;
      sdp_list_t *protocolElement = protocolList;

      while (protocolElement) {
        sdp_data_t *dataList = (sdp_data_t *)protocolElement->data;
        sdp_data_t *dataElement = dataList;
        int uuidProtocol = 0;

        while (dataElement) {
          if (SDP_IS_UUID(dataElement->dtd)) {
            uuidProtocol = sdp_uuid_to_proto(&dataElement->val.uuid);
          } else if (dataElement->dtd == SDP_UINT8) {
            if (uuidProtocol == RFCOMM_UUID) {
              *channel = dataElement->val.uint8;
              foundChannel = 1;
              stopSearching = 1;
            }
          }

          if (stopSearching) break;
          dataElement = dataElement->next;
        }

        if (stopSearching) break;
        protocolElement = protocolElement->next;
      }

      sdp_list_free(protocolList, NULL);
      if (stopSearching) break;
      protocolsElement = protocolsElement->next;
    }

    sdp_list_free(protocolsList, NULL);
  } else {
    logSystemError("sdp_get_access_protos");
  }

  return foundChannel;
}

static SocketDescriptor
bthNewL2capConnection (const bdaddr_t *address, int timeout) {
  SocketDescriptor socketDescriptor = socket(PF_BLUETOOTH, SOCK_SEQPACKET, BTPROTO_L2CAP);

  if (socketDescriptor != -1) {
    setCloseOnExec(socketDescriptor, 1);

    if (setBlockingIo(socketDescriptor, 0)) {
      struct sockaddr_l2 socketAddress = {
        .l2_family = AF_BLUETOOTH,
        .l2_bdaddr = *address,
        .l2_psm = htobs(SDP_PSM)
      };

      int connectResult = connectSocket(socketDescriptor,
                                        (struct sockaddr *)&socketAddress,
                                        sizeof(socketAddress),
                                        timeout);

      if (connectResult != -1) return socketDescriptor;
      logSystemProblem(bthGetConnectLogLevel(errno), "L2CAP connect");
    }

    setSocketNoLinger(socketDescriptor);
    close(socketDescriptor);
  } else {
    logSystemError("L2CAP socket");
  }

  return INVALID_SOCKET_DESCRIPTOR;
}

typedef struct {
  sdp_session_t *session;
  int *found;
  uint8_t *channel;
} BluetoothChannelDiscoveryData;

static void
bthHandleChannelDiscoveryResponse (
  uint8_t type, uint16_t status,
  uint8_t *response, size_t size,
  void *data
) {
  BluetoothChannelDiscoveryData *bcd = data;

  switch (status) {
    case 0:
      switch (type) {
        case SDP_SVC_SEARCH_ATTR_RSP: {
          uint8_t *nextByte = response;
          int bytesLeft = size;

          uint8_t dtd = 0;
          int dataLeft = 0;
          int headerLength = sdp_extract_seqtype(nextByte, bytesLeft, &dtd, &dataLeft);

          if (headerLength > 0) {
            nextByte += headerLength;
            bytesLeft -= headerLength;

            while (dataLeft > 0) {
              int stop = 0;
              int recordLength = 0;
              sdp_record_t *record = sdp_extract_pdu(nextByte, bytesLeft, &recordLength);

              if (record) {
                if (bthFindChannel(bcd->channel, record)) {
                  *bcd->found = 1;
                  stop = 1;
                }

                nextByte += recordLength;
                bytesLeft -= recordLength;
                dataLeft -= recordLength;

                sdp_record_free(record);
              } else {
                logSystemError("sdp_extract_pdu");
                stop = 1;
              }

              if (stop) break;
            }
          }

          break;
        }

        default:
          logMessage(LOG_ERR, "unexpected channel discovery response type: %u", type);
          break;
      }
      break;

    case 0XFFFF:
      errno = sdp_get_error(bcd->session);
      if (errno < 0) errno = EINVAL;
      logSystemError("channel discovery response");
      break;

    default:
      logMessage(LOG_ERR, "unexpected channel discovery response status: %u", status);
      break;
  }
}

int
bthDiscoverChannel (
  uint8_t *channel, BluetoothConnectionExtension *bcx,
  const void *uuidBytes, size_t uuidLength,
  int timeout
) {
  int foundChannel = 0;

  uuid_t uuid;
  sdp_list_t *searchList;

  sdp_uuid128_create(&uuid, uuidBytes);
  searchList = sdp_list_append(NULL, &uuid);

  if (searchList) {
    uint32_t attributesRange = 0X0000FFFF;
    sdp_list_t *attributesList = sdp_list_append(NULL, &attributesRange);

    if (attributesList) {
      if (LINUX_BLUETOOTH_CHANNEL_DISCOVER_ASYNCHRONOUS) {
        SocketDescriptor l2capSocket;
        TimePeriod period;
        startTimePeriod(&period, timeout);

        if ((l2capSocket = bthNewL2capConnection(&bcx->remoteAddress.rc_bdaddr, timeout)) != INVALID_SOCKET_DESCRIPTOR) {
          sdp_session_t *session = sdp_create(l2capSocket, 0);

          if (session) {
            BluetoothChannelDiscoveryData bcd = {
              .session = session,
              .found = &foundChannel,
              .channel = channel
            };

            if (sdp_set_notify(session, bthHandleChannelDiscoveryResponse, &bcd) != -1) {
              int queryStatus = sdp_service_search_attr_async(session, searchList,
                                                              SDP_ATTR_REQ_RANGE, attributesList);

              if (!queryStatus) {
                long int elapsed;

                while (!afterTimePeriod(&period, &elapsed)) {
                  if (!awaitSocketInput(l2capSocket, (timeout - elapsed))) break;
                  if (sdp_process(session) == -1) break;
                }
              } else {
                logSystemError("sdp_service_search_attr_async");
              }
            } else {
              logSystemError("sdp_set_notify");
            }

            sdp_close(session);
          } else {
            logSystemError("sdp_create");
          }

          close(l2capSocket);
        }
      } else {
        sdp_session_t *session = sdp_connect(BDADDR_ANY, &bcx->remoteAddress.rc_bdaddr, SDP_RETRY_IF_BUSY);

        if (session) {
          sdp_list_t *recordList = NULL;
          int queryStatus = sdp_service_search_attr_req(session, searchList,
                                                        SDP_ATTR_REQ_RANGE, attributesList,
                                                        &recordList);

          if (!queryStatus) {
            int stopSearching = 0;
            sdp_list_t *recordElement = recordList;

            while (recordElement) {
              sdp_record_t *record = (sdp_record_t *)recordElement->data;

              if (record) {
                if (bthFindChannel(channel, record)) {
                  foundChannel = 1;
                  stopSearching = 1;
                }

                sdp_record_free(record);
              } else {
                logMallocError();
                stopSearching = 1;
              }

              if (stopSearching) break;
              recordElement = recordElement->next;
            }

            sdp_list_free(recordList, NULL);
          } else {
            logSystemError("sdp_service_search_attr_req");
          }

          sdp_close(session);
        } else {
          logSystemError("sdp_connect");
        }
      }

      sdp_list_free(attributesList, NULL);
    } else {
      logMallocError();
    }

    sdp_list_free(searchList, NULL);
  } else {
    logMallocError();
  }

  return foundChannel;
}

int
bthMonitorInput (BluetoothConnection *connection, AsyncMonitorCallback *callback, void *data) {
  BluetoothConnectionExtension *bcx = connection->extension;

  bthCancelInputMonitor(bcx);
  if (!callback) return 1;
  return asyncMonitorSocketInput(&bcx->inputMonitor, bcx->socketDescriptor, callback, data);
}

int
bthPollInput (BluetoothConnectionExtension *bcx, int timeout) {
  return awaitSocketInput(bcx->socketDescriptor, timeout);
}

ssize_t
bthGetData (
  BluetoothConnectionExtension *bcx, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return readSocket(bcx->socketDescriptor, buffer, size, initialTimeout, subsequentTimeout);
}

ssize_t
bthPutData (BluetoothConnectionExtension *bcx, const void *buffer, size_t size) {
  return writeSocket(bcx->socketDescriptor, buffer, size);
}

char *
bthObtainDeviceName (uint64_t bda, int timeout) {
  char *name = NULL;
  int device = hci_get_route(NULL);

  if (device >= 0) {
    SocketDescriptor socketDescriptor = hci_open_dev(device);

    if (socketDescriptor >= 0) {
      int obtained = 0;
      bdaddr_t address;
      char buffer[HCI_MAX_NAME_LENGTH];

      bthMakeAddress(&address, bda);
      memset(buffer, 0, sizeof(buffer));

      if (LINUX_BLUETOOTH_NAME_OBTAIN_ASYNCHRONOUS) {
        if (setBlockingIo(socketDescriptor, 0)) {
          struct hci_filter oldFilter;
          socklen_t oldLength = sizeof(oldFilter);

          if (getsockopt(socketDescriptor, SOL_HCI, HCI_FILTER, &oldFilter, &oldLength) != -1) {
            uint16_t ogf = OGF_LINK_CTL;
            uint16_t ocf = OCF_REMOTE_NAME_REQ;
            uint16_t opcode = htobs(cmd_opcode_pack(ogf, ocf));
            struct hci_filter newFilter;

            hci_filter_clear(&newFilter);
            hci_filter_set_ptype(HCI_EVENT_PKT, &newFilter);
            hci_filter_set_event(EVT_CMD_STATUS, &newFilter);
            hci_filter_set_event(EVT_CMD_COMPLETE, &newFilter);
            hci_filter_set_event(EVT_REMOTE_NAME_REQ_COMPLETE, &newFilter);
            hci_filter_set_opcode(opcode, &newFilter);

            if (setsockopt(socketDescriptor, SOL_HCI, HCI_FILTER, &newFilter, sizeof(newFilter)) != -1) {
              remote_name_req_cp parameters;

              memset(&parameters, 0, sizeof(parameters));
              bacpy(&parameters.bdaddr, &address);
              parameters.pscan_rep_mode = 0X02;
              parameters.clock_offset = 0;

              if (hci_send_cmd(socketDescriptor, ogf, ocf, sizeof(parameters), &parameters) != -1) {
                long int elapsed = 0;
                TimePeriod period;
                startTimePeriod(&period, timeout);

                while (awaitSocketInput(socketDescriptor, (timeout - elapsed))) {
                  enum {
                    UNEXPECTED,
                    HANDLED,
                    DONE
                  } state = UNEXPECTED;

                  BluetoothPacket packet;
                  int result = read(socketDescriptor, &packet, sizeof(packet));

                  if (result == -1) {
                    if (errno == EAGAIN) continue;
                    if (errno == EINTR) continue;

                    logSystemError("read");
                    break;
                  }

                  switch (packet.fields.type) {
                    case HCI_EVENT_PKT: {
                      hci_event_hdr *header = &packet.fields.data.hciEvent.header;

                      switch (header->evt) {
                        case EVT_REMOTE_NAME_REQ_COMPLETE: {
                          evt_remote_name_req_complete *rn = &packet.fields.data.hciEvent.data.rn;

                          if (bacmp(&rn->bdaddr, &address) == 0) {
                            state = DONE;

                            if (!rn->status) {
                              size_t length = header->plen;

                              length -= rn->name - (unsigned char *)rn;
                              length = MIN(length, sizeof(rn->name));
                              length = MIN(length, sizeof(buffer)-1);

                              memcpy(buffer, rn->name, length);
                              buffer[length] = 0;
                              obtained = 1;
                            }
                          }

                          break;
                        }

                        case EVT_CMD_STATUS: {
                          evt_cmd_status *cs = &packet.fields.data.hciEvent.data.cs;

                          if (cs->opcode == opcode) {
                            state = HANDLED;

                            if (cs->status) {
                            }
                          }

                          break;
                        }

                        default:
                          logMessage(LOG_CATEGORY(BLUETOOTH_IO), "unexpected HCI event type: %u", header->evt);
                          break;
                      }

                      break;
                    }

                    default:
                      logMessage(LOG_CATEGORY(BLUETOOTH_IO), "unexpected Bluetooth packet type: %u", packet.fields.type);
                      break;
                  }

                  if (state == DONE) break;
                  if (state == UNEXPECTED) logBytes(LOG_WARNING, "unexpected Bluetooth packet", &packet, result);
                  if (afterTimePeriod(&period, &elapsed)) break;
                }
              } else {
                logSystemError("hci_send_cmd");
              }

              if (setsockopt(socketDescriptor, SOL_HCI, HCI_FILTER, &oldFilter, oldLength) == -1) {
                logSystemError("setsockopt[SOL_HCI,HCI_FILTER]");
              }
            } else {
              logSystemError("setsockopt[SOL_HCI,HCI_FILTER]");
            }
          } else {
            logSystemError("getsockopt[SOL_HCI,HCI_FILTER]");
          }
        }
      } else {
        int result = hci_read_remote_name(socketDescriptor, &address, sizeof(buffer), buffer, timeout);

        if (result >= 0) {
          obtained = 1;
        } else {
          logSystemError("hci_read_remote_name");
        }
      }

      if (obtained) {
        if (!(name = strdup(buffer))) {
          logMallocError();
        }
      }

      close(socketDescriptor);
    } else {
      logSystemError("hci_open_dev");
    }
  } else {
    logSystemError("hci_get_route");
  }

  return name;
}

#ifdef HAVE_PKG_DBUS
#include <dbus/dbus.h>

static void
logDBusError (const char *action, const DBusError *error) {
  const char *message = error->message;
  int length = strlen(message);

  while (length > 0) {
    char character = message[--length];
    if (character != '\n') break;
  }

  logMessage(LOG_CATEGORY(BLUETOOTH_IO),
             "DBus error: %s: %s: %.*s",
             action, error->name, length, message);
}
#endif /* HAVE_PKG_DBUS */

void
bthProcessDiscoveredDevices (
  DiscoveredBluetoothDeviceTester *testDevice, void *data
) {
#ifdef HAVE_PKG_DBUS
  int found = 0;

  DBusError error;
  DBusConnection *bus;
 
  dbus_error_init(&error);
  bus = dbus_bus_get(DBUS_BUS_SYSTEM, &error);

  if (dbus_error_is_set(&error)) {
    logDBusError("get bus", &error);
    dbus_error_free(&error);
  } else if (!bus) {
    logMallocError();
  } else {
    DBusMessage *getManagedObjects = dbus_message_new_method_call(
      "org.bluez", "/",
      "org.freedesktop.DBus.ObjectManager",
      "GetManagedObjects"
    );

    if (!getManagedObjects) {
      logMallocError();
    } else {
      DBusMessage *managedObjects = dbus_connection_send_with_reply_and_block(
        bus, getManagedObjects, -1, &error
      );

      dbus_message_unref(getManagedObjects);
      getManagedObjects = NULL;

      if (dbus_error_is_set(&error)) {
        logDBusError("send message", &error);
        dbus_error_free(&error);
      } else if (!managedObjects) {
        logMallocError();
      } else {
        DBusMessageIter args;

        if (dbus_message_iter_init(managedObjects, &args) == FALSE) {
          logMessage(LOG_ERR, "reply has no arguments");
        } else if (dbus_message_iter_get_arg_type(&args) != DBUS_TYPE_ARRAY) {
          logMessage(LOG_ERR, "expecting an array");
        } else {
          DBusMessageIter objects;
          dbus_message_iter_recurse(&args, &objects);

          while (dbus_message_iter_get_arg_type(&objects) == DBUS_TYPE_DICT_ENTRY) {
            DBusMessageIter object;
            dbus_message_iter_recurse(&objects, &object);

            if (dbus_message_iter_get_arg_type(&object) == DBUS_TYPE_OBJECT_PATH) {
              // DBus path to talk to this object
              dbus_message_iter_next(&object);

              if (dbus_message_iter_get_arg_type(&object) == DBUS_TYPE_ARRAY) {
                DBusMessageIter interfaces;
                dbus_message_iter_recurse(&object, &interfaces);

                while (dbus_message_iter_get_arg_type(&interfaces) == DBUS_TYPE_DICT_ENTRY) {
                  DBusMessageIter interface;
                  dbus_message_iter_recurse(&interfaces, &interface);

                  if (dbus_message_iter_get_arg_type(&interface) == DBUS_TYPE_STRING) {
                    const char *interfaceName;
                    dbus_message_iter_get_basic(&interface, &interfaceName);

                    if (strcmp(interfaceName, "org.bluez.Device1") == 0) {
                      dbus_message_iter_next(&interface);

                      if (dbus_message_iter_get_arg_type(&interface) == DBUS_TYPE_ARRAY) {
                        DiscoveredBluetoothDevice device;
                        memset(&device, 0, sizeof(device));

                        DBusMessageIter properties;
                        dbus_message_iter_recurse(&interface, &properties);

                        while (DBUS_TYPE_DICT_ENTRY == dbus_message_iter_get_arg_type(&properties)) {
                          DBusMessageIter property;
                          dbus_message_iter_recurse(&properties, &property);

                          if (dbus_message_iter_get_arg_type(&property) == DBUS_TYPE_STRING) {
                            const char *propertyName;

                            dbus_message_iter_get_basic(&property, &propertyName);
                            dbus_message_iter_next(&property);

                            if (dbus_message_iter_get_arg_type(&property) == DBUS_TYPE_VARIANT) {
                              DBusMessageIter variant;
                              dbus_message_iter_recurse(&property, &variant);

                              if ((strcmp(propertyName, "Address") == 0) &&
                                  (dbus_message_iter_get_arg_type(&variant) == DBUS_TYPE_STRING)) {
                                const char *address;
                                dbus_message_iter_get_basic(&variant, &address);
                                bthParseAddress(&device.address, address);
                              } else if ((strcmp(propertyName, "Name") == 0) &&
                                         (dbus_message_iter_get_arg_type(&variant) == DBUS_TYPE_STRING)) {
                                const char *name;
                                dbus_message_iter_get_basic(&variant, &name);
                                device.name = name;
                              } else if ((strcmp(propertyName, "Paired") == 0) &&
                                         (dbus_message_iter_get_arg_type(&variant) == DBUS_TYPE_BOOLEAN)) {
                                dbus_bool_t paired;
                                dbus_message_iter_get_basic(&variant, &paired);
                                device.paired = paired == TRUE;
                              }
                            }
                          }

                          dbus_message_iter_next(&properties);
                        }

                        if (device.address) {
                          if (testDevice(&device, data)) {
                            found = 1;
                          }
                        }
                      }
                    }
                  }

                  if (found) break;
                  dbus_message_iter_next(&interfaces);
                }
              }
            }

            if (found) break;
            dbus_message_iter_next(&objects);
          }
        }

        dbus_message_unref(managedObjects);
        managedObjects = NULL;
      }
    }

    dbus_connection_unref(bus);
    bus = NULL;
  }
#endif /* HAVE_PKG_DBUS */
}
