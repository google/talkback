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

#ifndef BRLTTY_INCLUDED_USB_TYPES
#define BRLTTY_INCLUDED_USB_TYPES

#include "serial_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

/* Descriptor types. */
typedef enum {
  UsbDescriptorType_Device        = 0X01,
  UsbDescriptorType_Configuration = 0X02,
  UsbDescriptorType_String        = 0X03,
  UsbDescriptorType_Interface     = 0X04,
  UsbDescriptorType_Endpoint      = 0X05,
  UsbDescriptorType_HID           = 0X21,
  UsbDescriptorType_Report        = 0X22
} UsbDescriptorType;

/* Descriptor sizes. */
typedef enum {
  UsbDescriptorSize_Device        = 18,
  UsbDescriptorSize_Configuration =  9,
  UsbDescriptorSize_String        =  2,
  UsbDescriptorSize_Interface     =  9,
  UsbDescriptorSize_Endpoint      =  7,
  UsbDescriptorSize_HID           =  6,
  UsbDescriptorSize_Class         =  3
} UsbDescriptorSize;

typedef enum {
  UsbSpecificationVersion_1_0 = 0X0100,
  UsbSpecificationVersion_1_1 = 0X0110,
  UsbSpecificationVersion_2_0 = 0X0200,
  UsbSpecificationVersion_3_0 = 0X0300
} UsbSpecificationVersion;

/* Configuration attributes (bmAttributes). */
typedef enum {
  UsbConfigurationAttribute_BusPowered   = 0X80,
  UsbConfigurationAttribute_SelfPowered  = 0X40,
  UsbConfigurationAttribute_RemoteWakeup = 0X20
} UsbConfigurationAttribute;

/* Device and interface classes (bDeviceClass, bInterfaceClass). */
typedef enum {
  UsbClass_PerInterface = 0X00,
  UsbClass_Audio        = 0X01,
  UsbClass_Comm         = 0X02,
  UsbClass_Hid          = 0X03,
  UsbClass_Physical     = 0X05,
  UsbClass_Printer      = 0X07,
  UsbClass_MassStorage  = 0X08,
  UsbClass_Hub          = 0X09,
  UsbClass_Data         = 0X0A,
  UsbClass_AppSpec      = 0XFE,
  UsbClass_VendorSpec   = 0XFF
} UsbClass;

/* Endpoint numbers (bEndpointAddress). */
typedef enum {
  UsbEndpointNumber_Mask = 0X0F
} UsbEndpointNumber;
#define USB_ENDPOINT_NUMBER(descriptor) ((descriptor)->bEndpointAddress & UsbEndpointNumber_Mask)

/* Endpoint directions (bEndpointAddress). */
typedef enum {
  UsbEndpointDirection_Output = 0X00,
  UsbEndpointDirection_Input  = 0X80,
  UsbEndpointDirection_Mask   = 0X80
} UsbEndpointDirection;
#define USB_ENDPOINT_DIRECTION(descriptor) ((descriptor)->bEndpointAddress & UsbEndpointDirection_Mask)

/* Endpoint transfer types (bmAttributes). */
typedef enum {
  UsbEndpointTransfer_Control     = 0X00,
  UsbEndpointTransfer_Isochronous = 0X01,
  UsbEndpointTransfer_Bulk        = 0X02,
  UsbEndpointTransfer_Interrupt   = 0X03,
  UsbEndpointTransfer_Mask        = 0X03
} UsbEndpointTransfer;
#define USB_ENDPOINT_TRANSFER(descriptor) ((descriptor)->bmAttributes & UsbEndpointTransfer_Mask)

/* Endpoint isochronous types (bmAttributes). */
typedef enum {
  UsbEndpointIsochronous_Asynchronous = 0X04,
  UsbEndpointIsochronous_Adaptable    = 0X08,
  UsbEndpointIsochronous_Synchronous  = 0X0C,
  UsbEndpointIsochronous_Mask         = 0X0C
} UsbEndpointIsochronous;
#define USB_ENDPOINT_ISOCHRONOUS(descriptor) ((descriptor)->bmAttributes & UsbEndpointIsochronous_Mask)

/* Control transfer recipients. */
typedef enum {
  UsbControlRecipient_Device    = 0X00,
  UsbControlRecipient_Interface = 0X01,
  UsbControlRecipient_Endpoint  = 0X02,
  UsbControlRecipient_Other     = 0X03,
  UsbControlRecipient_Mask      = 0X1F
} UsbControlRecipient;

/* Control transfer types. */
typedef enum {
  UsbControlType_Standard = 0X00,
  UsbControlType_Class    = 0X20,
  UsbControlType_Vendor   = 0X40,
  UsbControlType_Reserved = 0X60,
  UsbControlType_Mask     = 0X60
} UsbControlType;

/* Transfer directions. */
typedef enum {
  UsbControlDirection_Output = 0X00,
  UsbControlDirection_Input  = 0X80,
  UsbControlDirection_Mask   = 0X80
} UsbControlDirection;

/* Standard control requests. */
typedef enum {
  UsbStandardRequest_GetStatus        = 0X00,
  UsbStandardRequest_ClearFeature     = 0X01,
  UsbStandardRequest_GetState         = 0X02,
  UsbStandardRequest_SetFeature       = 0X03,
  UsbStandardRequest_SetAddress       = 0X05,
  UsbStandardRequest_GetDescriptor    = 0X06,
  UsbStandardRequest_SetDescriptor    = 0X07,
  UsbStandardRequest_GetConfiguration = 0X08,
  UsbStandardRequest_SetConfiguration = 0X09,
  UsbStandardRequest_GetInterface     = 0X0A,
  UsbStandardRequest_SetInterface     = 0X0B,
  UsbStandardRequest_SynchFrame       = 0X0C
} UsbStandardRequest;

/* Standard features. */
typedef enum {
  UsbFeature_Endpoint_Stall      = 0X00,
  UsbFeature_Device_RemoteWakeup = 0X01
} UsbFeature;

typedef struct {
  uint8_t bLength;         /* Descriptor size in bytes. */
  uint8_t bDescriptorType; /* Descriptor type. */
} PACKED UsbDescriptorHeader;

typedef struct {
  uint8_t bLength;            /* Descriptor size in bytes (18). */
  uint8_t bDescriptorType;    /* Descriptor type (1 == device). */
  uint16_t bcdUSB;            /* USB revision number. */
  uint8_t bDeviceClass;       /* Device class. */
  uint8_t bDeviceSubClass;    /* Device subclass. */
  uint8_t bDeviceProtocol;    /* Device protocol. */
  uint8_t bMaxPacketSize0;    /* Maximum packet size in bytes for endpoint 0. */
  uint16_t idVendor;          /* Vendor identifier. */
  uint16_t idProduct;         /* Product identifier. */
  uint16_t bcdDevice;         /* Product revision number. */
  uint8_t iManufacturer;      /* String index for manufacturer name. */
  uint8_t iProduct;           /* String index for product description. */
  uint8_t iSerialNumber;      /* String index for serial number. */
  uint8_t bNumConfigurations; /* Number of configurations. */
} PACKED UsbDeviceDescriptor;

typedef struct {
  uint8_t bLength;             /* Descriptor size in bytes (9). */
  uint8_t bDescriptorType;     /* Descriptor type (2 == configuration). */
  uint16_t wTotalLength;       /* Block size in bytes for all descriptors. */
  uint8_t bNumInterfaces;      /* Number of interfaces. */
  uint8_t bConfigurationValue; /* Configuration number. */
  uint8_t iConfiguration;      /* String index for configuration description. */
  uint8_t bmAttributes;        /* Configuration attributes. */
  uint8_t bMaxPower;           /* Maximum power in 2 milliamp units. */
} PACKED UsbConfigurationDescriptor;

typedef struct {
  uint8_t bLength;         /* Descriptor size in bytes (2 + numchars/2). */
  uint8_t bDescriptorType; /* Descriptor type (3 == string). */
  uint16_t wData[127];     /* 16-bit characters. */
} PACKED UsbStringDescriptor;

typedef struct {
  uint8_t bLength;            /* Descriptor size in bytes (9). */
  uint8_t bDescriptorType;    /* Descriptor type (4 == interface). */
  uint8_t bInterfaceNumber;   /* Interface number. */
  uint8_t bAlternateSetting;  /* Interface alternative. */
  uint8_t bNumEndpoints;      /* Number of endpoints. */
  uint8_t bInterfaceClass;    /* Interface class. */
  uint8_t bInterfaceSubClass; /* Interface subclass. */
  uint8_t bInterfaceProtocol; /* Interface protocol. */
  uint8_t iInterface;         /* String index for interface description. */
} PACKED UsbInterfaceDescriptor;

typedef struct {
  uint8_t bLength;          /* Descriptor size in bytes (7, 9 for audio). */
  uint8_t bDescriptorType;  /* Descriptor type (5 == endpoint). */
  uint8_t bEndpointAddress; /* Endpoint number (ored with 0X80 if input. */
  uint8_t bmAttributes;     /* Endpoint type and attributes. */
  uint16_t wMaxPacketSize;  /* Maximum packet size in bytes. */
  uint8_t bInterval;        /* Maximum interval in milliseconds between transfers. */
  uint8_t bRefresh;
  uint8_t bSynchAddress;
} PACKED UsbEndpointDescriptor;

typedef struct {
  uint8_t bDescriptorType;
  uint16_t wDescriptorLength;
} PACKED UsbClassDescriptor;

typedef struct {
  uint8_t bLength;          /* Descriptor size in bytes (6). */
  uint8_t bDescriptorType;  /* Descriptor type (33 == HID). */
  uint16_t bcdHID;
  uint8_t bCountryCode;
  uint8_t bNumDescriptors;
  UsbClassDescriptor descriptors[(0XFF - UsbDescriptorSize_HID) / UsbDescriptorSize_Class];
} PACKED UsbHidDescriptor;

typedef union {
  UsbDescriptorHeader header;
  UsbDeviceDescriptor device;
  UsbConfigurationDescriptor configuration;
  UsbStringDescriptor string;
  UsbInterfaceDescriptor interface;
  UsbEndpointDescriptor endpoint;
  UsbHidDescriptor hid;
  unsigned char bytes[0XFF];
} UsbDescriptor;

typedef struct {
  uint8_t bRequestType; /* Recipient, direction, and type. */
  uint8_t bRequest;     /* Request code. */
  uint16_t wValue;      /* Request value. */
  uint16_t wIndex;      /* Recipient number (language for strings). */
  uint16_t wLength;     /* Data length in bytes. */
} PACKED UsbSetupPacket;

#define BEGIN_USB_STRING_LIST(name) static const char *const name[] = {
#define END_USB_STRING_LIST NULL};

typedef struct {
  const void *data;
  const SerialParameters *serial;

  const char *const *manufacturers;
  const char *const *products;

  uint16_t version;
  uint16_t vendor;
  uint16_t product;
  uint16_t parentVendor;
  uint16_t parentProduct;

  unsigned char configuration;
  unsigned char interface;
  unsigned char alternative;
  unsigned char inputEndpoint;
  unsigned char outputEndpoint;

  unsigned char disableAutosuspend:1;
  unsigned char disableEndpointReset:1;
  unsigned char verifyInterface:1;
  unsigned char resetDevice:1;
} UsbChannelDefinition;

#define BEGIN_USB_CHANNEL_DEFINITIONS static const UsbChannelDefinition usbChannelDefinitions[] = {
#define END_USB_CHANNEL_DEFINITIONS { .vendor=0 } };

typedef struct UsbDeviceStruct UsbDevice;
typedef struct UsbChooseChannelDataStruct UsbChooseChannelData;
typedef int UsbDeviceChooser (UsbDevice *device, UsbChooseChannelData *data);

typedef struct {
  void *const buffer;
  const size_t size;

  ssize_t length;
} UsbInputFilterData;

typedef struct UsbSerialDataStruct UsbSerialData;
typedef int UsbInputFilter (UsbInputFilterData *data);

typedef struct {
  const char *name;

  int (*enableAdapter) (UsbDevice *device);
  void (*disableAdapter) (UsbDevice *device);

  int (*makeData) (UsbDevice *device, UsbSerialData **serialData);
  void (*destroyData) (UsbSerialData *usd);

  int (*setLineConfiguration) (UsbDevice *device, unsigned int baud, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity, SerialFlowControl flowControl);
  int (*setLineProperties) (UsbDevice *device, unsigned int baud, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity);
  int (*setBaud) (UsbDevice *device, unsigned int baud);
  int (*setDataFormat) (UsbDevice *device, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity);
  int (*setFlowControl) (UsbDevice *device, SerialFlowControl flow);

  int (*setDtrState) (UsbDevice *device, int state);
  int (*setRtsState) (UsbDevice *device, int state);

  UsbInputFilter *inputFilter;
  ssize_t (*writeData) (UsbDevice *device, const void *data, size_t size);
} UsbSerialOperations;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_USB_TYPES */
