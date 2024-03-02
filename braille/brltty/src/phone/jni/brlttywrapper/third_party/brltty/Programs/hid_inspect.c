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

#include <string.h>

#include "log.h"
#include "strfmt.h"
#include "bitmask.h"
#include "hid_defs.h"
#include "hid_items.h"
#include "hid_tables.h"
#include "hid_inspect.h"

static int
hidCompareTableEntriesByValue (const void *element1, const void *element2) {
  const HidTableEntryHeader *const *header1 = element1;
  const HidTableEntryHeader *const *header2 = element2;

  HidUnsignedValue value1 = (*header1)->value;
  HidUnsignedValue value2 = (*header2)->value;

  if (value1 < value2) return -1;
  if (value1 > value2) return 1;
  return 0;
}

const void *
hidTableEntry (HidTable *table, HidUnsignedValue value) {
  if (!table->sorted) {
    if (!(table->sorted = malloc(ARRAY_SIZE(table->sorted, table->count)))) {
      logMallocError();
      return NULL;
    }

    {
      const void *entry = table->entries;
      const HidTableEntryHeader **header = table->sorted;

      for (unsigned int index=0; index<table->count; index+=1) {
        *header++ = entry;
        entry += table->size;
      }
    }

    qsort(
      table->sorted, table->count, sizeof(*table->sorted),
      hidCompareTableEntriesByValue
    );
  }

  unsigned int from = 0;
  unsigned int to = table->count;

  while (from < to) {
    unsigned int current = (from + to) / 2;
    const HidTableEntryHeader *header = table->sorted[current];
    if (value == header->value) return header;

    if (value < header->value) {
      to = current;
    } else {
      from = current + 1;
    }
  }

  return NULL;
}

static int
hidCompareReportIdentifiers (const void *element1, const void *element2) {
  const HidReportIdentifier *identifier1 = element1;
  const HidReportIdentifier *identifier2 = element2;

  if (*identifier1 < *identifier2) return -1;
  if (*identifier1 > *identifier2) return 1;
  return 0;
}

HidReports *
hidGetReports (const HidItemsDescriptor *items) {
  HidReportIdentifier identifiers[UINT8_MAX];
  unsigned char count = 0;

  BITMASK(haveIdentifier, UINT8_MAX+1, char);
  BITMASK_ZERO(haveIdentifier);

  const unsigned char *nextByte = items->bytes;
  size_t bytesLeft = items->count;

  while (1) {
    HidItem item;
    if (!hidNextItem(&item, &nextByte, &bytesLeft)) break;

    switch (item.tag) {
      case HID_ITM_ReportID: {
        HidUnsignedValue identifier = item.value.u;

        if (!identifier) continue;
        if (identifier > UINT8_MAX) continue;
        if (BITMASK_TEST(haveIdentifier, identifier)) continue;

        BITMASK_SET(haveIdentifier, identifier);
        identifiers[count++] = identifier;
        break;
      }

      case HID_ITM_Input:
      case HID_ITM_Output:
      case HID_ITM_Feature:
        if (!count) identifiers[count++] = 0;
        break;
    }
  }

  if (count > 1) {
    qsort(
      identifiers, count, sizeof(identifiers[0]),
      hidCompareReportIdentifiers
    );
  }

  HidReports *reports;
  size_t size = sizeof(*reports);
  size += count;

  if ((reports = malloc(size))) {
    memset(reports, 0, sizeof(*reports));

    reports->count = count;
    memcpy(reports->identifiers, identifiers, count);

    return reports;
  } else {
    logMallocError();
  }

  return NULL;
}

STR_BEGIN_FORMATTER(hidFormatUsageFlags, HidUnsignedValue flags)
  typedef struct {
    const char *on;
    const char *off;
    HidUnsignedValue bit;
  } FlagEntry;

  static const FlagEntry flagTable[] = {
    { .bit = HID_USG_FLG_CONSTANT,
      .on = "const",
      .off = "data"
    },

    { .bit = HID_USG_FLG_VARIABLE,
      .on = "var",
      .off = "array"
    },

    { .bit = HID_USG_FLG_RELATIVE,
      .on = "rel",
      .off = "abs"
    },

    { .bit = HID_USG_FLG_WRAP,
      .on = "wrap",
    },

    { .bit = HID_USG_FLG_NON_LINEAR,
      .on = "nonlin",
    },

    { .bit = HID_USG_FLG_NO_PREFERRED,
      .on = "nopref",
    },

    { .bit = HID_USG_FLG_NULL_STATE,
      .on = "null",
    },

    { .bit = HID_USG_FLG_VOLATILE,
      .on = "volatile",
    },

    { .bit = HID_USG_FLG_BUFFERED_BYTE,
      .on = "buffbyte",
    },
  };

  const FlagEntry *flag = flagTable;
  const FlagEntry *end = flag + ARRAY_COUNT(flagTable);

  while (flag < end) {
    const char *name = (flags & flag->bit)? flag->on: flag->off;

    if (name) {
      if (STR_LENGTH > 0) STR_PRINTF(" ");
      STR_PRINTF("%s", name);
    }

    flag += 1;
  }
STR_END_FORMATTER

static int
hidListItem (const char *line, void *data) {
  return logMessage((LOG_CATEGORY(HID_IO) | LOG_DEBUG), "%s", line);
}

int
hidListItems (const HidItemsDescriptor *items, HidItemLister *listItem, void *data) {
  if (!listItem) listItem = hidListItem;
  const char *label = "Items List";

  {
    char line[0X40];
    STR_BEGIN(line, sizeof(line));
    STR_PRINTF("Begin %s: Bytes:%"PRIsize, label, items->count);
    STR_END;
    if (!listItem(line, data)) return 0;
  }

  unsigned int itemCount = 0;
  const unsigned char *nextByte = items->bytes;
  size_t bytesLeft = items->count;

  int decOffsetWidth;
  int hexOffsetWidth;
  {
    unsigned int maximumOffset = bytesLeft;
    char buffer[0X20];

    decOffsetWidth = snprintf(buffer, sizeof(buffer), "%u", maximumOffset);
    hexOffsetWidth = snprintf(buffer, sizeof(buffer), "%x", maximumOffset);
  }

  HidUnsignedValue usagePage = 0;

  while (1) {
    unsigned int offset = nextByte - items->bytes;
    HidItem item;
    int ok = hidNextItem(&item, &nextByte, &bytesLeft);

    char line[0X100];
    STR_BEGIN(line, sizeof(line));

    STR_PRINTF(
      "Item: %*u (0X%.*X):",
      decOffsetWidth, offset, hexOffsetWidth, offset
    );

    if (ok) {
      itemCount += 1;

      switch (item.tag) {
        case HID_ITM_UsagePage:
          usagePage = item.value.u;
          break;
      }

      {
        const HidItemTagEntry *tag = hidItemTagEntry(item.tag);

        if (tag) {
          STR_PRINTF(" %s", tag->header.name);
        } else {
          STR_PRINTF(" unknown item tag: 0X%02X", item.tag);
        }
      }

      if (item.valueSize > 0) {
        HidUnsignedValue hexValue = item.value.u & ((UINT64_C(1) << (item.valueSize * 8)) - 1);
        int hexPrecision = item.valueSize * 2;

        STR_PRINTF(
          " = %" PRId32 " (0X%.*" PRIX32 ")",
          item.value.s, hexPrecision, hexValue
        );
      }

      {
        HidUnsignedValue value = item.value.u;

        char name[0X100];
        STR_BEGIN(name, sizeof(name));

        switch (item.tag) {
          case HID_ITM_UsagePage: {
            const HidUsagePageEntry *upg = hidUsagePageEntry(value);
            if (upg) STR_PRINTF("%s", upg->header.name);
            break;
          }

          case HID_ITM_UsageMinimum:
          case HID_ITM_UsageMaximum:
          case HID_ITM_Usage: {
            HidUnsignedValue usage = item.value.u;

            HidUnsignedValue page;
            const HidUsagePageEntry *upg;

            if (item.valueSize == 4) {
              page = usage >> 0X10;
              usage &= UINT16_MAX;
            } else {
              page = usagePage;
            }

            if ((upg = hidUsagePageEntry(page))) {
              HidTable *utb = upg->usageTable;

              if (utb) {
                const HidUsageEntryHeader *usg = hidTableEntry(utb, usage);

                if (usg) {
                  STR_PRINTF("%s", usg->header.name);

                  {
                    const HidUsageTypeEntry *type = hidUsageTypeEntry(usg->usageType);
                    if (type) STR_PRINTF(" (%s)", type->header.name);
                  }
                }
              }
            }

            if (page != usagePage) {
              if (*name) STR_PRINTF(" ");
              STR_PRINTF("[");

              if (upg) {
                STR_PRINTF("%s", upg->header.name);
              } else {
                STR_PRINTF("0X%02"PRIX32, page);
              }

              STR_PRINTF("]");
            }

            break;
          }

          case HID_ITM_Collection: {
            const HidCollectionTypeEntry *col = hidCollectionTypeEntry(value);
            if (col) STR_PRINTF("%s", col->header.name);
            break;
          }

          case HID_ITM_Input:
          case HID_ITM_Output:
          case HID_ITM_Feature: {
            STR_FORMAT(hidFormatUsageFlags, value);
            break;
          }
        }

        STR_END;
        if (*name) STR_PRINTF(": %s", name);
      }
    } else if (bytesLeft) {
      STR_PRINTF(" incomplete:");
      const unsigned char *end = nextByte + bytesLeft;

      while (nextByte < end) {
        STR_PRINTF(" %02X", *nextByte++);
      }
    } else {
      STR_PRINTF(" end");
    }

    STR_END;
    if (!listItem(line, data)) return 0;
    if (!ok) break;
  }

  {
    char line[0X40];
    STR_BEGIN(line, sizeof(line));
    STR_PRINTF("End %s: Items:%u", label, itemCount);
    STR_END;
    return listItem(line, data);
  }
}
