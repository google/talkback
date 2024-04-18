/*
 * Copyright (C) 2024 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
#include <jni.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <wchar.h>

#include "brldefs-hid.h"
#include "third_party/brltty/Headers/bitmask.h"
#include "third_party/brltty/Headers/brl_base.h"
#include "third_party/brltty/Headers/brl_driver.h"
#include "third_party/brltty/Headers/brl_types.h"
#include "third_party/brltty/Headers/brl_utils.h"
#include "third_party/brltty/Headers/gio_types.h"
#include "third_party/brltty/Headers/hid_defs.h"
#include "third_party/brltty/Headers/hid_items.h"
#include "third_party/brltty/Headers/hid_types.h"
#include "third_party/brltty/Headers/io_generic.h"
#include "third_party/brltty/Headers/ktb_types.h"
#include "third_party/brltty/Headers/log.h"
#include "third_party/brltty/Programs/gio_internal.h"

struct BrailleDataStruct {
  struct {
    unsigned char count;
    KEYS_BITMASK(mask);
  } pressedKeys;

  struct {
    unsigned char rewrite;
    unsigned char cells[MAX_OUTPUT_SIZE];
  } text;

  struct {
    // The HID report ID used by both input and output. Currently expects both
    // to be the same.
    uint32_t reportId;
    // The size of the HID input report.
    uint32_t inputSizeBytes;
    // Map each input report bit to the HID usage it represents.
    uint32_t inputReportUsages[MAX_INPUT_SIZE];
    // Map each input report bit to the Key (internal number) it represents.
    uint32_t inputReportKeys[MAX_INPUT_SIZE];
    // The first (lowest) bit number of the contiguous group of routing keys.
    uint32_t inputRoutingFirstBit;
  } reportInfo;
};

// Parses the Braille display's HID report descriptor, in order to understand
// how to parse input reports and prepare output reports.
static int probeHidDisplay(BrailleDisplay *brl, HidItemsDescriptor *items) {
  memset(brl->data->reportInfo.inputReportUsages, 0,
         MAX_INPUT_SIZE * sizeof(brl->data->reportInfo.inputReportUsages[0]));
  memset(brl->data->reportInfo.inputReportKeys, 0,
         MAX_INPUT_SIZE * sizeof(brl->data->reportInfo.inputReportKeys[0]));
  brl->data->reportInfo.inputRoutingFirstBit = -1;

  // If you'd like to print all items in the HID report then call
  // hid_inspect#hidListItems()

  // These variables save attributes from the current stack of items:
  //   The current Usage Page.
  uint32_t usagePage = 0;
  //   Number of bits per usage.
  uint32_t reportSize = 0;
  //   Number of usages in this stack.
  uint32_t reportCount = 0;
  //   The Usages in this stack, stored as a list of individual usages.
  uint32_t usages[MAX_USAGE_COUNT];
  uint32_t usageIndex = 0;
  //   The Usages in this stack, stored as a minimum and maximum value.
  uint32_t usageMin = 0;
  uint32_t usageMax = 0;
  //   The report ID. This is implicitly zero by default, until some value
  //   provided by the descriptor.
  uint32_t reportId = 0;

  //   The current bit of the INPUT report, used for writing to the
  //   inputReportUsages map.
  uint32_t inputReportBit = 0;

  // These variables will be "learned" while parsing the report descriptor;
  // set them to "unset" defaults so that parsing can detect if an inconsistency
  // occurs.
  //   Stores the number of output cells so that brltty knows how to prepare
  //   output cells.
  brl->textColumns = -1;
  //   The report ID that will be providing Braille Display input and output.
  brl->data->reportInfo.reportId = -1;

  const unsigned char *nextByte = items->bytes;
  size_t bytesLeft = items->count;
  HidItem item;
  int parsingError = 0;
  while (1) {
    if (!hidNextItem(&item, &nextByte, &bytesLeft)) {
      break;
    }
    if (item.tag == HID_ITM_UsagePage) {
      usagePage = item.value.u;
    }
    if (item.tag == HID_ITM_Collection) {
      // Collections help differentiate between groupings of usages, e.g.
      // between multiple rows of output braille cells. All devices we've tested
      // so far provide their BD usages in one collection, so this driver does
      // not yet support differentiating between collections.
      //
      // The type of collection would have been specified by usage items before
      // this collection item, so reset the usage data structure (since as noted
      // above we are ignoring collection designations).
      usageIndex = 0;
    }
    if (item.tag == HID_ITM_ReportID) {
      reportId = item.value.u;
    }
    if (item.tag == HID_ITM_Usage) {
      usageMin = usageMax = -1;
      uint32_t usage = item.value.u;
      usages[usageIndex++] = usage;
    }
    if (item.tag == HID_ITM_UsageMinimum) {
      usageMin = item.value.u;
    }
    if (item.tag == HID_ITM_UsageMaximum) {
      usageMax = item.value.u;
    }
    if (item.tag == HID_ITM_ReportSize) {
      reportSize = item.value.u;
    }
    if (item.tag == HID_ITM_ReportCount) {
      reportCount = item.value.u;
    }
    if (item.tag == HID_ITM_Input || item.tag == HID_ITM_Output ||
        item.tag == HID_ITM_Feature) {
      // Reset the usage index now that we're going to process the usages array.
      usageIndex = 0;
      // Set the BD report ID to the current reportId.
      if (usagePage == HID_UPG_Braille) {
        if (brl->data->reportInfo.reportId != -1 &&
            brl->data->reportInfo.reportId != reportId) {
          logMessage(LOG_ERR,
                     "Found multiple report IDs that include Braille usages");
          parsingError = 1;
          break;
        }
        brl->data->reportInfo.reportId = reportId;
      }
    }

    if (item.tag == HID_ITM_Input) {
      if (reportId == brl->data->reportInfo.reportId) {
        // Skip past constant bits
        if ((item.value.u & HID_USG_FLG_CONSTANT) == HID_USG_FLG_CONSTANT) {
          inputReportBit += reportSize * reportCount;
          continue;
        }
        // Skip past usages from unexpected pages.
        if (usagePage != HID_UPG_Braille && usagePage != HID_UPG_Button) {
          inputReportBit += reportSize * reportCount;
          continue;
        }
        // Fail if we get a usage of unexpected type or size
        if (reportSize != 1) {
          logMessage(LOG_ERR, "Unexpected input item input size %u != 1",
                     reportSize);
          parsingError = 1;
          break;
        }
        if ((item.value.u & HID_USG_FLG_VARIABLE) != HID_USG_FLG_VARIABLE) {
          logMessage(LOG_ERR, "Unexpected non-variable input item");
          parsingError = 1;
          break;
        }
        // Fail if we get a usage range that doesn't match the report count
        if (usageMin != -1 && (usageMin + reportCount - 1) != usageMax) {
          logMessage(LOG_ERR, "Invalid usage range: min=%u max=%u count=%u",
                     usageMin, usageMax, reportCount);
          parsingError = 1;
          break;
        }
        if (inputReportBit > MAX_INPUT_SIZE) {
          logMessage(LOG_ERR, "Unexpected input report with more than %u bits",
                     MAX_INPUT_SIZE);
          parsingError = 1;
          break;
        }
        for (int i = 0; i < reportCount; i++) {
          if (usageMin != -1) {
            brl->data->reportInfo.inputReportUsages[inputReportBit++] =
                usageMin++;
          } else {
            brl->data->reportInfo.inputReportUsages[inputReportBit++] =
                usages[i];
          }
        }
      }
    }
    if (item.tag == HID_ITM_Output) {
      if (usagePage == HID_UPG_Braille) {
        if (reportId != brl->data->reportInfo.reportId) {
          logMessage(LOG_ERR,
                     "Unexpected differing output and input report IDs");
          parsingError = 1;
          break;
        }
        if (reportSize != 8) {
          logMessage(LOG_ERR, "Invalid output bit size %u", reportSize);
          parsingError = 1;
          break;
        }
        if (brl->textColumns != -1) {
          logMessage(LOG_ERR, "Unexpected received multiple BD output reports");
          parsingError = 1;
          break;
        }
        brl->textColumns = reportCount;
      }
    }
  }
  free(items);
  if (parsingError) {
    logMessage(LOG_ERR, "There were parsing errors.");
    return 0;
  }
  if (brl->data->reportInfo.reportId == -1) {
    logMessage(LOG_ERR, "Could not find a Braille Display report ID");
    return 0;
  }
  if (brl->textColumns == -1) {
    logMessage(LOG_ERR, "Could not find the Braille Display output cell count");
    return 0;
  }
  for (int i = 0; i < MAX_INPUT_SIZE; i++) {
    logMessage(LOG_DEBUG, "bit=%d report=%i", i,
               brl->data->reportInfo.inputReportUsages[i]);

    // While parsing the descript we built up map inputReportUsages from
    // bit->usage. However, brltty doesn't use usages to describe key events:
    // brltty uses a key table that was provided by brl_construct. This logic
    // builds up a new map from bit->key where the key values come from the key
    // table that we provided to brltty. This allows the driver to map from
    // INPUT bit to the brltty-known key name.
    for (int j = 0; j < KEY_MAP_COUNT; j++) {
      if (KEY_MAP[j][0] == brl->data->reportInfo.inputReportUsages[i]) {
        brl->data->reportInfo.inputReportKeys[i] = KEY_MAP[j][1];
      }
    }
    // Routing keys are handled differently. They all use the same usage,
    // while their bit number (starting from the first one) defines the actual
    // routing key number.
    if (brl->data->reportInfo.inputReportUsages[i] == HID_USG_BRL_RouterKey) {
      if (brl->data->reportInfo.inputRoutingFirstBit == -1) {
        brl->data->reportInfo.inputRoutingFirstBit = i;
      } else if (brl->data->reportInfo.inputReportUsages[i - 1] !=
                 HID_USG_BRL_RouterKey) {
        // Expect that all routing key INPUTs are sent as a contiguous group, so
        // return error if the descriptor describes something like "... ROUTING
        // DOT1 ROUTING ...".
        logMessage(LOG_ERR,
                   "Unexpected non-contiguous group of router keys at "
                   "%d with previous entry %u and first bit %d",
                   i, brl->data->reportInfo.inputReportUsages[i - 1],
                   brl->data->reportInfo.inputRoutingFirstBit);
        return 0;
      }
    }
  }

  int inputSizeBytes = (inputReportBit + 7) / 8;
  int hasNumberedReport = brl->data->reportInfo.reportId != 0;
  if (hasNumberedReport != 0) {
    // The first byte of input should contain the report ID, then all other
    // bytes should be parsed as input.
    brl->data->reportInfo.inputSizeBytes = inputSizeBytes + 1;
  } else {
    // The entire input report should be parsed as input.
    brl->data->reportInfo.inputSizeBytes = inputSizeBytes;
  }
  // Zero-out the input key mask used to track the current state of input key
  // presses.
  BITMASK_ZERO(brl->data->pressedKeys.mask);
  return 1;
}

// Enqueues a key event to brltty's internal key processing logic.
// brltty waits for all keys to be released, then looks at the combined set of
// key-down events to understand what key combination was pressed by looking
// up possible key combinations from the HID.ktb keytable.
static int handleKeyEvent(BrailleDisplay *brl, unsigned char key, int press) {
  KeyGroup group;
  if (key < HID_KEY_ROUTING) {
    group = HID_GRP_NavigationKeys;
  } else {
    group = HID_GRP_RoutingKeys;
    key -= HID_KEY_ROUTING;
  }
  return enqueueKeyEvent(brl, group, key, press);
}

// Possibly enqueues a key-down action.
static int handleKeyPress(BrailleDisplay *brl, unsigned char key) {
  if (BITMASK_TEST(brl->data->pressedKeys.mask, key)) return 0;

  BITMASK_SET(brl->data->pressedKeys.mask, key);
  brl->data->pressedKeys.count += 1;

  handleKeyEvent(brl, key, 1);
  return 1;
}

// Possibly enqueues a key-action action.
static int handleKeyRelease(BrailleDisplay *brl, unsigned char key) {
  if (!BITMASK_TEST(brl->data->pressedKeys.mask, key)) return 0;

  BITMASK_CLEAR(brl->data->pressedKeys.mask, key);
  brl->data->pressedKeys.count -= 1;

  handleKeyEvent(brl, key, 0);
  return 1;
}

// Parses a HID input report into brltty key actions.
// Called by brltty when it wants to parse an input byte array.
static void handlePressedKeysArray(BrailleDisplay *brl, unsigned char *keys) {
  // Per HIDRAW spec if input descriptor report number is not zero then the
  // first byte in the input should be the report number.
  int hasNumberedReport = brl->data->reportInfo.reportId != 0;
  if (hasNumberedReport && keys[0] != brl->data->reportInfo.reportId) {
    logMessage(LOG_WARNING, "Unexpected input report %u", keys[0]);
    return;
  }

  int numInputBytes = hasNumberedReport
                          ? brl->data->reportInfo.inputSizeBytes - 1
                          : brl->data->reportInfo.inputSizeBytes;
  const unsigned char *byte = hasNumberedReport ? keys + 1 : keys;
  for (int byteNum = 0; byteNum < numInputBytes; byteNum++) {
    for (int bit = 0; bit <= 7; bit++) {
      int bitNum = byteNum * 8 + bit;
      char key;
      if (brl->data->reportInfo.inputReportUsages[bitNum] ==
          HID_USG_BRL_RouterKey) {
        int routingKeyNum = bitNum - brl->data->reportInfo.inputRoutingFirstBit;
        key = HID_KEY_ROUTING + routingKeyNum;
      } else {
        key = brl->data->reportInfo.inputReportKeys[bitNum];
      }
      if (key != 0) {
        if ((*byte) & (1 << bit)) {
          logMessage(LOG_DEBUG, "Pressed bit %d usage %u", bitNum,
                     brl->data->reportInfo.inputReportUsages[bitNum]);
          handleKeyPress(brl, key);
        } else {
          handleKeyRelease(brl, key);
        }
      }
    }
    byte++;
  }
}

static int writeHidCells(BrailleDisplay *brl, const unsigned char *cells,
                         unsigned char cellCount) {
  // HIDRAW expects the report ID in the first byte, followed by the
  // output report.
  int bufferSize = cellCount + 1;
  unsigned char buffer[bufferSize];
  buffer[0] = brl->data->reportInfo.reportId;
  memcpy(buffer + 1, cells, cellCount);
  return brl->gioEndpoint->handleMethods->writeData(
      brl->gioEndpoint->handle, buffer, bufferSize, /*timeout=*/0);
}

// Standard functions expected by brltty for any brltty driver. These were
// essentially copied the Humanware driver with minimal modifications.

static int connectResource(BrailleDisplay *brl, const char *identifier) {
  static const HidModelEntry hidModelTable[] = {
      {
          // Model name is not used to control driver behavior, so always
          // expect "HID" as set by hid_android.c#getGenericHIDDeviceName().
          .name = "HID",
      },
      {.name = NULL, .vendor = 0}};

  GioDescriptor descriptor;
  gioInitializeDescriptor(&descriptor);
  descriptor.hid.modelTable = hidModelTable;

  return connectBrailleResource(brl, identifier, &descriptor, NULL) ? 1 : 0;
}

static int brl_construct(BrailleDisplay *brl, char **parameters,
                         const char *device) {
  if ((brl->data = malloc(sizeof(*brl->data)))) {
    memset(brl->data, 0, sizeof(*brl->data));

    if (connectResource(brl, device)) {
      HidItemsDescriptor *items = brl->gioEndpoint->handleMethods->getHidDescriptor(
              brl->gioEndpoint->handle);
      if (probeHidDisplay(brl, items)) {
        setBrailleKeyTable(brl, &keyTableDefinition_HID);
        makeOutputTable(dotsTable_ISO11548_1);
        brl->data->text.rewrite = 1;
        return 1;
      }
      disconnectBrailleResource(brl, NULL);
    }
    free(brl->data);
    brl->data = NULL;
  } else {
    logMallocError();
  }
  return 0;
}

static void brl_destruct(BrailleDisplay *brl) {
  disconnectBrailleResource(brl, NULL);
  free(brl->data);
}

// Called by brltty when it wants to write output to the Braille display.
static int brl_writeWindow(BrailleDisplay *brl, const wchar_t *text) {
  const size_t count = brl->textColumns;
  if (cellsHaveChanged(brl->data->text.cells, brl->buffer, count, NULL, NULL,
                       &brl->data->text.rewrite)) {
    unsigned char cells[count];

    translateOutputCells(cells, brl->data->text.cells, count);
    if (!writeHidCells(brl, cells, count)) return 0;
  }
  return 1;
}

static int brl_readCommand(BrailleDisplay *brl,
                           KeyTableCommandContext context) {
  unsigned char packet[MAX_INPUT_SIZE];
  while (1) {
    size_t length = brl->gioEndpoint->handleMethods->readData(
        brl->gioEndpoint->handle, packet, MAX_INPUT_SIZE,
        /*initialTimeout unused*/ 0, /*subsequentTimeout unused*/ 0);
    if (length == 0) {
      break;
    }
    handlePressedKeysArray(brl, packet);
  }
  return EOF;
}

