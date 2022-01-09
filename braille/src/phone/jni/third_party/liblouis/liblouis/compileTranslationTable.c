/* liblouis Braille Translation and Back-Translation Library

   Based on the Linux screenreader BRLTTY, copyright (C) 1999-2006 by The
   BRLTTY Team

   Copyright (C) 2004, 2005, 2006 ViewPlus Technologies, Inc. www.viewplus.com
   Copyright (C) 2004, 2005, 2006 JJB Software, Inc. www.jjb-software.com
   Copyright (C) 2016 Mike Gray, American Printing House for the Blind
   Copyright (C) 2016 Davy Kager, Dedicon

   This file is part of liblouis.

   liblouis is free software: you can redistribute it and/or modify it
   under the terms of the GNU Lesser General Public License as published
   by the Free Software Foundation, either version 2.1 of the License, or
   (at your option) any later version.

   liblouis is distributed in the hope that it will be useful, but
   WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
   Lesser General Public License for more details.

   You should have received a copy of the GNU Lesser General Public
   License along with liblouis. If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * @file
 * @brief Read and compile translation tables
 */

#include <stddef.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <sys/stat.h>

#include "internal.h"
#include "config.h"

#define QUOTESUB 28 /* Stand-in for double quotes in strings */

/* needed to make debuggin easier */
#ifdef DEBUG
wchar_t wchar;
#endif

/* The following variables and functions make it possible to specify the
 * path on which all tables for liblouis and all files for liblouisutdml,
 * in their proper directories, will be found.
 */

static char *dataPathPtr;

char *EXPORT_CALL
lou_setDataPath(const char *path) {
	static char dataPath[MAXSTRING];
	dataPathPtr = NULL;
	if (path == NULL) return NULL;
	strcpy(dataPath, path);
	dataPathPtr = dataPath;
	return dataPathPtr;
}

char *EXPORT_CALL
lou_getDataPath(void) {
	return dataPathPtr;
}

/* End of dataPath code. */

static int
eqasc2uni(const unsigned char *a, const widechar *b, const int len) {
	int k;
	for (k = 0; k < len; k++)
		if ((widechar)a[k] != b[k]) return 0;
	return 1;
}

typedef struct CharsString {
	widechar length;
	widechar chars[MAXSTRING];
} CharsString;

static int errorCount;
static int warningCount;

typedef struct TranslationTableChainEntry {
	struct TranslationTableChainEntry *next;
	TranslationTableHeader *table;
	int tableListLength;
	char tableList[1];
} TranslationTableChainEntry;

static TranslationTableChainEntry *translationTableChain = NULL;

typedef struct DisplayTableChainEntry {
	struct DisplayTableChainEntry *next;
	DisplayTableHeader *table;
	int tableListLength;
	char tableList[1];
} DisplayTableChainEntry;

static DisplayTableChainEntry *displayTableChain = NULL;

/* predefined character classes */
static const char *characterClassNames[] = {
	"space",
	"letter",
	"digit",
	"punctuation",
	"uppercase",
	"lowercase",
	"math",
	"sign",
	"litdigit",
	NULL,
};

// names that may not be used for custom attributes
static const char *reservedAttributeNames[] = {
	"numericnocontchars",
	"numericnocontchar",
	"numericnocont",
	"midendnumericmodechars",
	"midendnumericmodechar",
	"midendnumericmode",
	"numericmodechars",
	"numericmodechar",
	"numericmode",
	"capsmodechars",
	"capsmodechar",
	"capsmode",
	"emphmodechars",
	"emphmodechar",
	"emphmode",
	"seqdelimiter",
	"seqbeforechars",
	"seqbeforechar",
	"seqbefore",
	"seqafterchars",
	"seqafterchar",
	"seqafter",
	"noletsign",
	"noletsignbefore",
	"noletsignafter",
	NULL,
};

static const char *opcodeNames[CTO_None] = {
	"include",
	"locale",
	"undefined",
	"capsletter",
	"begcapsword",
	"endcapsword",
	"begcaps",
	"endcaps",
	"begcapsphrase",
	"endcapsphrase",
	"lencapsphrase",
	"letsign",
	"noletsignbefore",
	"noletsign",
	"noletsignafter",
	"numsign",
	"numericmodechars",
	"midendnumericmodechars",
	"numericnocontchars",
	"seqdelimiter",
	"seqbeforechars",
	"seqafterchars",
	"seqafterpattern",
	"seqafterexpression",
	"emphclass",
	"emphletter",
	"begemphword",
	"endemphword",
	"begemph",
	"endemph",
	"begemphphrase",
	"endemphphrase",
	"lenemphphrase",
	"capsmodechars",
	"emphmodechars",
	"begcomp",
	"compbegemph1",
	"compendemph1",
	"compbegemph2",
	"compendemph2",
	"compbegemph3",
	"compendemph3",
	"compcapsign",
	"compbegcaps",
	"compendcaps",
	"endcomp",
	"nocontractsign",
	"multind",
	"compdots",
	"comp6",
	"class",
	"after",
	"before",
	"noback",
	"nofor",
	"empmatchbefore",
	"empmatchafter",
	"swapcc",
	"swapcd",
	"swapdd",
	"space",
	"digit",
	"punctuation",
	"math",
	"sign",
	"letter",
	"uppercase",
	"lowercase",
	"grouping",
	"uplow",
	"litdigit",
	"display",
	"replace",
	"context",
	"correct",
	"pass2",
	"pass3",
	"pass4",
	"repeated",
	"repword",
	"rependword",
	"capsnocont",
	"always",
	"exactdots",
	"nocross",
	"syllable",
	"nocont",
	"compbrl",
	"literal",
	"largesign",
	"word",
	"partword",
	"joinnum",
	"joinword",
	"lowword",
	"contraction",
	"sufword",
	"prfword",
	"begword",
	"begmidword",
	"midword",
	"midendword",
	"endword",
	"prepunc",
	"postpunc",
	"begnum",
	"midnum",
	"endnum",
	"decpoint",
	"hyphen",
	// "apostrophe",
	// "initial",
	"nobreak",
	"match",
	"backmatch",
	"attribute",
};

static short opcodeLengths[CTO_None] = { 0 };

static void
compileError(FileInfo *nested, const char *format, ...);

static void
free_tablefiles(char **tables);

static int
getAChar(FileInfo *nested) {
	/* Read a big endian, little endian or ASCII 8 file and convert it to
	 * 16- or 32-bit unsigned integers */
	int ch1 = 0, ch2 = 0;
	widechar character;
	if (nested->encoding == ascii8)
		if (nested->status == 2) {
			nested->status++;
			return nested->checkencoding[1];
		}
	while ((ch1 = fgetc(nested->in)) != EOF) {
		if (nested->status < 2) nested->checkencoding[nested->status] = ch1;
		nested->status++;
		if (nested->status == 2) {
			if (nested->checkencoding[0] == 0xfe && nested->checkencoding[1] == 0xff)
				nested->encoding = bigEndian;
			else if (nested->checkencoding[0] == 0xff && nested->checkencoding[1] == 0xfe)
				nested->encoding = littleEndian;
			else if (nested->checkencoding[0] < 128 && nested->checkencoding[1] < 128) {
				nested->encoding = ascii8;
				return nested->checkencoding[0];
			} else {
				compileError(nested,
						"encoding is neither big-endian, little-endian nor ASCII 8.");
				ch1 = EOF;
				break;
				;
			}
			continue;
		}
		switch (nested->encoding) {
		case noEncoding:
			break;
		case ascii8:
			return ch1;
			break;
		case bigEndian:
			ch2 = fgetc(nested->in);
			if (ch2 == EOF) break;
			character = (widechar)(ch1 << 8) | ch2;
			return (int)character;
			break;
		case littleEndian:
			ch2 = fgetc(nested->in);
			if (ch2 == EOF) break;
			character = (widechar)(ch2 << 8) | ch1;
			return (int)character;
			break;
		}
		if (ch1 == EOF || ch2 == EOF) break;
	}
	return EOF;
}

int EXPORT_CALL
_lou_getALine(FileInfo *nested) {
	/* Read a line of widechar's from an input file */
	int ch;
	int pch = 0;
	nested->linelen = 0;
	while ((ch = getAChar(nested)) != EOF) {
		if (ch == 13) continue;
		if (pch == '\\' && ch == 10) {
			nested->linelen--;
			pch = ch;
			continue;
		}
		if (ch == 10 || nested->linelen >= MAXSTRING - 1) break;
		nested->line[nested->linelen++] = (widechar)ch;
		pch = ch;
	}
	nested->line[nested->linelen] = 0;
	nested->linepos = 0;
	if (ch == EOF) return 0;
	nested->lineNumber++;
	return 1;
}

static inline int
atEndOfLine(FileInfo *nested) {
	return nested->linepos >= nested->linelen;
}

static inline int
atTokenDelimiter(FileInfo *nested) {
	return nested->line[nested->linepos] <= 32;
}

static int
getToken(FileInfo *nested, CharsString *result, const char *description, int *lastToken) {
	/* Find the next string of contiguous non-whitespace characters. If this
	 * is the last token on the line, return 2 instead of 1. */
	while (!atEndOfLine(nested) && atTokenDelimiter(nested)) nested->linepos++;
	result->length = 0;
	while (!atEndOfLine(nested) && !atTokenDelimiter(nested)) {
		int maxlen = MAXSTRING;
		if (result->length >= maxlen) {
			compileError(nested, "more than %d characters (bytes)", maxlen);
			return 0;
		} else
			result->chars[result->length++] = nested->line[nested->linepos++];
	}
	if (!result->length) {
		/* Not enough tokens */
		if (description) compileError(nested, "%s not specified.", description);
		return 0;
	}
	result->chars[result->length] = 0;
	while (!atEndOfLine(nested) && atTokenDelimiter(nested)) nested->linepos++;
	return (*lastToken = atEndOfLine(nested)) ? 2 : 1;
}

static void
compileError(FileInfo *nested, const char *format, ...) {
#ifndef __SYMBIAN32__
	char buffer[MAXSTRING];
	va_list arguments;
	va_start(arguments, format);
	vsnprintf(buffer, sizeof(buffer), format, arguments);
	va_end(arguments);
	if (nested)
		_lou_logMessage(LOU_LOG_ERROR, "%s:%d: error: %s", nested->fileName,
				nested->lineNumber, buffer);
	else
		_lou_logMessage(LOU_LOG_ERROR, "error: %s", buffer);
	errorCount++;
#endif
}

static void
compileWarning(FileInfo *nested, const char *format, ...) {
#ifndef __SYMBIAN32__
	char buffer[MAXSTRING];
	va_list arguments;
	va_start(arguments, format);
	vsnprintf(buffer, sizeof(buffer), format, arguments);
	va_end(arguments);
	if (nested)
		_lou_logMessage(LOU_LOG_WARN, "%s:%d: warning: %s", nested->fileName,
				nested->lineNumber, buffer);
	else
		_lou_logMessage(LOU_LOG_WARN, "warning: %s", buffer);
	warningCount++;
#endif
}

static int
allocateSpaceInTranslationTable(FileInfo *nested, TranslationTableOffset *offset,
		int count, TranslationTableHeader **table) {
	/* allocate memory for table and expand previously allocated memory if necessary */
	int spaceNeeded = ((count + OFFSETSIZE - 1) / OFFSETSIZE) * OFFSETSIZE;
	TranslationTableOffset newSize = (*table)->bytesUsed + spaceNeeded;
	TranslationTableOffset size = (*table)->tableSize;
	if (newSize > size) {
		TranslationTableHeader *newTable;
		newSize += (newSize / OFFSETSIZE);
		newTable = realloc(*table, newSize);
		if (!newTable) {
			compileError(nested, "Not enough memory for translation table.");
			_lou_outOfMemory();
		}
		memset(((unsigned char *)newTable) + size, 0, newSize - size);
		/* update references to the old table */
		{
			TranslationTableChainEntry *entry;
			for (entry = translationTableChain; entry != NULL; entry = entry->next)
				if (entry->table == *table)
					entry->table = (TranslationTableHeader *)newTable;
		}
		*table = (TranslationTableHeader *)newTable;
		(*table)->tableSize = newSize;
	}
	if (offset != NULL) {
		*offset = ((*table)->bytesUsed - sizeof(**table)) / OFFSETSIZE;
		(*table)->bytesUsed += spaceNeeded;
	}
	return 1;
}

static int
allocateSpaceInDisplayTable(FileInfo *nested, TranslationTableOffset *offset, int count,
		DisplayTableHeader **table) {
	/* allocate memory for table and expand previously allocated memory if necessary */
	int spaceNeeded = ((count + OFFSETSIZE - 1) / OFFSETSIZE) * OFFSETSIZE;
	TranslationTableOffset newSize = (*table)->bytesUsed + spaceNeeded;
	TranslationTableOffset size = (*table)->tableSize;
	if (newSize > size) {
		DisplayTableHeader *newTable;
		newSize += (newSize / OFFSETSIZE);
		newTable = realloc(*table, newSize);
		if (!newTable) {
			compileError(nested, "Not enough memory for display table.");
			_lou_outOfMemory();
		}
		memset(((unsigned char *)newTable) + size, 0, newSize - size);
		/* update references to the old table */
		{
			DisplayTableChainEntry *entry;
			for (entry = displayTableChain; entry != NULL; entry = entry->next)
				if (entry->table == *table) entry->table = (DisplayTableHeader *)newTable;
		}
		*table = (DisplayTableHeader *)newTable;
		(*table)->tableSize = newSize;
	}
	if (offset != NULL) {
		*offset = ((*table)->bytesUsed - sizeof(**table)) / OFFSETSIZE;
		(*table)->bytesUsed += spaceNeeded;
	}
	return 1;
}

static int
allocateTranslationTable(FileInfo *nested, TranslationTableHeader **table) {
	/* Allocate memory for the table and a guess on the number of rules */
	const TranslationTableOffset startSize = 2 * sizeof(**table);
	if (*table) return 1;
	TranslationTableOffset bytesUsed =
			sizeof(**table) + OFFSETSIZE; /* So no offset is ever zero */
	if (!(*table = malloc(startSize))) {
		compileError(nested, "Not enough memory");
		if (*table != NULL) free(*table);
		*table = NULL;
		_lou_outOfMemory();
	}
	memset(*table, 0, startSize);
	(*table)->tableSize = startSize;
	(*table)->bytesUsed = bytesUsed;
	return 1;
}

static int
allocateDisplayTable(FileInfo *nested, DisplayTableHeader **table) {
	/* Allocate memory for the table and a guess on the number of rules */
	const TranslationTableOffset startSize = 2 * sizeof(**table);
	if (*table) return 1;
	TranslationTableOffset bytesUsed =
			sizeof(**table) + OFFSETSIZE; /* So no offset is ever zero */
	if (!(*table = malloc(startSize))) {
		compileError(nested, "Not enough memory");
		if (*table != NULL) free(*table);
		*table = NULL;
		_lou_outOfMemory();
	}
	memset(*table, 0, startSize);
	(*table)->tableSize = startSize;
	(*table)->bytesUsed = bytesUsed;
	return 1;
}

/* Look up a character or dot pattern. Although the algorithms are almost identical,
 * different tables are needed for characters and dots because of the possibility of
 * conflicts. */

static TranslationTableCharacter *
getChar(widechar c, TranslationTableHeader *table) {
	TranslationTableCharacter *character;
	unsigned long int makeHash = _lou_charHash(c);
	TranslationTableOffset bucket = table->characters[makeHash];
	while (bucket) {
		character = (TranslationTableCharacter *)&table->ruleArea[bucket];
		if (character->realchar == c) return character;
		bucket = character->next;
	}
	return NULL;
}

static TranslationTableCharacter *
getDots(widechar d, TranslationTableHeader *table) {
	TranslationTableCharacter *character;
	unsigned long int makeHash = _lou_charHash(d);
	TranslationTableOffset bucket = table->dots[makeHash];
	while (bucket) {
		character = (TranslationTableCharacter *)&table->ruleArea[bucket];
		if (character->realchar == d) return character;
		bucket = character->next;
	}
	return NULL;
}

static TranslationTableCharacter *
putChar(FileInfo *nested, widechar c, TranslationTableHeader **table) {
	/* See if a character is in the appropriate table. If not, insert it. In either case,
	 * return a pointer to it. */
	TranslationTableOffset bucket;
	TranslationTableCharacter *character;
	TranslationTableCharacter *oldchar;
	TranslationTableOffset offset;
	unsigned long int makeHash;
	if ((character = getChar(c, *table))) return character;
	if (!allocateSpaceInTranslationTable(nested, &offset, sizeof(*character), table))
		return NULL;
	character = (TranslationTableCharacter *)&(*table)->ruleArea[offset];
	memset(character, 0, sizeof(*character));
	character->realchar = c;
	makeHash = _lou_charHash(c);
	bucket = (*table)->characters[makeHash];
	if (!bucket)
		(*table)->characters[makeHash] = offset;
	else {
		oldchar = (TranslationTableCharacter *)&(*table)->ruleArea[bucket];
		while (oldchar->next)
			oldchar = (TranslationTableCharacter *)&(*table)->ruleArea[oldchar->next];
		oldchar->next = offset;
	}
	return character;
}

static TranslationTableCharacter *
putDots(FileInfo *nested, widechar d, TranslationTableHeader **table) {
	/* See if a dot pattern is in the appropriate table. If not, insert it. In either
	 * case, return a pointer to it. */
	TranslationTableOffset bucket;
	TranslationTableCharacter *character;
	TranslationTableCharacter *oldchar;
	TranslationTableOffset offset;
	unsigned long int makeHash;
	if ((character = getDots(d, *table))) return character;
	if (!allocateSpaceInTranslationTable(nested, &offset, sizeof(*character), table))
		return NULL;
	character = (TranslationTableCharacter *)&(*table)->ruleArea[offset];
	memset(character, 0, sizeof(*character));
	character->realchar = d;
	makeHash = _lou_charHash(d);
	bucket = (*table)->dots[makeHash];
	if (!bucket)
		(*table)->dots[makeHash] = offset;
	else {
		oldchar = (TranslationTableCharacter *)&(*table)->ruleArea[bucket];
		while (oldchar->next)
			oldchar = (TranslationTableCharacter *)&(*table)->ruleArea[oldchar->next];
		oldchar->next = offset;
	}
	return character;
}

/* Look up a character-dots mapping in a display table. */

static CharDotsMapping *
getDotsForChar(widechar c, const DisplayTableHeader *table) {
	CharDotsMapping *cdPtr;
	unsigned long int makeHash = _lou_charHash(c);
	TranslationTableOffset bucket = table->charToDots[makeHash];
	while (bucket) {
		cdPtr = (CharDotsMapping *)&table->ruleArea[bucket];
		if (cdPtr->lookFor == c) return cdPtr;
		bucket = cdPtr->next;
	}
	return NULL;
}

static CharDotsMapping *
getCharForDots(widechar d, const DisplayTableHeader *table) {
	CharDotsMapping *cdPtr;
	unsigned long int makeHash = _lou_charHash(d);
	TranslationTableOffset bucket = table->dotsToChar[makeHash];
	while (bucket) {
		cdPtr = (CharDotsMapping *)&table->ruleArea[bucket];
		if (cdPtr->lookFor == d) return cdPtr;
		bucket = cdPtr->next;
	}
	return NULL;
}

widechar EXPORT_CALL
_lou_getDotsForChar(widechar c, const DisplayTableHeader *table) {
	CharDotsMapping *cdPtr = getDotsForChar(c, table);
	if (cdPtr) return cdPtr->found;
	return LOU_DOTS;
}

widechar EXPORT_CALL
_lou_getCharForDots(widechar d, const DisplayTableHeader *table) {
	CharDotsMapping *cdPtr = getCharForDots(d, table);
	if (cdPtr) return cdPtr->found;
	return '\0';
}

static int
putCharDotsMapping(FileInfo *nested, widechar c, widechar d, DisplayTableHeader **table) {
	if (!getDotsForChar(c, *table)) {
		TranslationTableOffset bucket;
		CharDotsMapping *cdPtr;
		CharDotsMapping *oldcdPtr = NULL;
		TranslationTableOffset offset;
		unsigned long int makeHash;
		if (!allocateSpaceInDisplayTable(nested, &offset, sizeof(*cdPtr), table))
			return 0;
		cdPtr = (CharDotsMapping *)&(*table)->ruleArea[offset];
		cdPtr->next = 0;
		cdPtr->lookFor = c;
		cdPtr->found = d;
		makeHash = _lou_charHash(c);
		bucket = (*table)->charToDots[makeHash];
		if (!bucket)
			(*table)->charToDots[makeHash] = offset;
		else {
			oldcdPtr = (CharDotsMapping *)&(*table)->ruleArea[bucket];
			while (oldcdPtr->next)
				oldcdPtr = (CharDotsMapping *)&(*table)->ruleArea[oldcdPtr->next];
			oldcdPtr->next = offset;
		}
	}
	if (!getCharForDots(d, *table)) {
		TranslationTableOffset bucket;
		CharDotsMapping *cdPtr;
		CharDotsMapping *oldcdPtr = NULL;
		TranslationTableOffset offset;
		unsigned long int makeHash;
		if (!allocateSpaceInDisplayTable(nested, &offset, sizeof(*cdPtr), table))
			return 0;
		cdPtr = (CharDotsMapping *)&(*table)->ruleArea[offset];
		cdPtr->next = 0;
		cdPtr->lookFor = d;
		cdPtr->found = c;
		makeHash = _lou_charHash(d);
		bucket = (*table)->dotsToChar[makeHash];
		if (!bucket)
			(*table)->dotsToChar[makeHash] = offset;
		else {
			oldcdPtr = (CharDotsMapping *)&(*table)->ruleArea[bucket];
			while (oldcdPtr->next)
				oldcdPtr = (CharDotsMapping *)&(*table)->ruleArea[oldcdPtr->next];
			oldcdPtr->next = offset;
		}
	}
	return 1;
}

static inline const char *
getPartName(int actionPart) {
	return actionPart ? "action" : "test";
}

static int
passFindCharacters(FileInfo *nested, widechar *instructions, int end,
		widechar **characters, int *length) {
	int IC = 0;
	int lookback = 0;

	*characters = NULL;
	*length = 0;

	while (IC < end) {
		widechar instruction = instructions[IC];

		switch (instruction) {
		case pass_string:
		case pass_dots: {
			int count = instructions[IC + 1];
			IC += 2;
			if (count > lookback) {
				*characters = &instructions[IC + lookback];
				*length = count - lookback;
				return 1;
			} else {
				lookback -= count;
			}
			IC += count;
			continue;
		}

		case pass_attributes:
			IC += 7;
			if (instructions[IC - 2] == instructions[IC - 1] &&
					instructions[IC - 1] <= lookback) {
				lookback -= instructions[IC - 1];
				continue;
			}
			goto NO_CHARACTERS;

		case pass_swap:
			IC += 2;
			/* fall through */

		case pass_groupstart:
		case pass_groupend:
		case pass_groupreplace:
			IC += 3;

		NO_CHARACTERS : { return 1; }

		case pass_eq:
		case pass_lt:
		case pass_gt:
		case pass_lteq:
		case pass_gteq:
			IC += 3;
			continue;

		case pass_lookback:
			lookback += instructions[IC + 1];
			IC += 2;
			continue;

		case pass_not:
		case pass_startReplace:
		case pass_endReplace:
		case pass_first:
		case pass_last:
		case pass_copy:
		case pass_omit:
		case pass_plus:
		case pass_hyphen:
			IC += 1;
			continue;

		case pass_endTest:
			goto NO_CHARACTERS;

		default:
			compileError(nested, "unhandled test suboperand: \\x%02x", instruction);
			return 0;
		}
	}
	goto NO_CHARACTERS;
}

/* The following functions are called by addRule to handle various cases. */

static void
addForwardRuleWithSingleChar(FileInfo *nested, TranslationTableOffset newRuleOffset,
		TranslationTableRule *newRule, TranslationTableHeader **table) {
	/* direction = 0, newRule->charslen = 1 */
	TranslationTableRule *currentRule;
	TranslationTableOffset *currentOffsetPtr;
	TranslationTableCharacter *character;
	if (newRule->opcode == CTO_CompDots || newRule->opcode == CTO_Comp6) return;
	// get the character from the table, or if the character is not defined yet, define it
	// (without adding attributes)
	if (newRule->opcode >= CTO_Pass2 && newRule->opcode <= CTO_Pass4)
		character = putDots(nested, newRule->charsdots[0], table);
	else {
		character = putChar(nested, newRule->charsdots[0], table);
		if (character->attributes & CTC_Letter &&
				(newRule->opcode == CTO_WholeWord || newRule->opcode == CTO_LargeSign)) {
			if ((*table)->noLetsignCount < LETSIGNSIZE)
				(*table)->noLetsign[(*table)->noLetsignCount++] = newRule->charsdots[0];
		}
		// if the new rule is a character definition rule, set the main definition rule of
		// this character to it (possibly overwriting previous definition rules)
		// adding the attributes to the character has already been done elsewhere
		if (newRule->opcode >= CTO_Space && newRule->opcode < CTO_UpLow)
			character->definitionRule = newRuleOffset;
	}
	// add the new rule to the list of rules associated with this character
	// if the new rule is a character definition rule, it is inserted at the end of the
	// list
	// otherwise it is inserted before the first character definition rule
	currentOffsetPtr = &character->otherRules;
	while (*currentOffsetPtr) {
		currentRule = (TranslationTableRule *)&(*table)->ruleArea[*currentOffsetPtr];
		if (currentRule->charslen == 0) break;
		if (currentRule->opcode >= CTO_Space && currentRule->opcode < CTO_UpLow)
			if (!(newRule->opcode >= CTO_Space && newRule->opcode < CTO_UpLow)) break;
		currentOffsetPtr = &currentRule->charsnext;
	}
	newRule->charsnext = *currentOffsetPtr;
	*currentOffsetPtr = newRuleOffset;
}

static void
addForwardRuleWithMultipleChars(TranslationTableOffset newRuleOffset,
		TranslationTableRule *newRule, TranslationTableHeader *table) {
	/* direction = 0 newRule->charslen > 1 */
	TranslationTableRule *currentRule = NULL;
	TranslationTableOffset *currentOffsetPtr =
			&table->forRules[_lou_stringHash(&newRule->charsdots[0], 0, NULL)];
	while (*currentOffsetPtr) {
		currentRule = (TranslationTableRule *)&table->ruleArea[*currentOffsetPtr];
		if (newRule->charslen > currentRule->charslen) break;
		if (newRule->charslen == currentRule->charslen)
			if ((currentRule->opcode == CTO_Always) && (newRule->opcode != CTO_Always))
				break;
		currentOffsetPtr = &currentRule->charsnext;
	}
	newRule->charsnext = *currentOffsetPtr;
	*currentOffsetPtr = newRuleOffset;
}

static void
addBackwardRuleWithSingleCell(FileInfo *nested, widechar cell,
		TranslationTableOffset newRuleOffset, TranslationTableRule *newRule,
		TranslationTableHeader **table) {
	/* direction = 1, newRule->dotslen = 1 */
	TranslationTableRule *currentRule;
	TranslationTableOffset *currentOffsetPtr;
	TranslationTableCharacter *dots;
	if (newRule->opcode == CTO_SwapCc || newRule->opcode == CTO_Repeated)
		return; /* too ambiguous */
	// get the cell from the table, or if the cell is not defined yet, define it (without
	// adding attributes)
	dots = putDots(nested, cell, table);
	if (newRule->opcode >= CTO_Space && newRule->opcode < CTO_UpLow)
		dots->definitionRule = newRuleOffset;
	currentOffsetPtr = &dots->otherRules;
	while (*currentOffsetPtr) {
		currentRule = (TranslationTableRule *)&(*table)->ruleArea[*currentOffsetPtr];
		if (newRule->charslen > currentRule->charslen || currentRule->dotslen == 0) break;
		if (currentRule->opcode >= CTO_Space && currentRule->opcode < CTO_UpLow)
			if (!(newRule->opcode >= CTO_Space && newRule->opcode < CTO_UpLow)) break;
		currentOffsetPtr = &currentRule->dotsnext;
	}
	newRule->dotsnext = *currentOffsetPtr;
	*currentOffsetPtr = newRuleOffset;
}

static void
addBackwardRuleWithMultipleCells(widechar *cells, int count,
		TranslationTableOffset newRuleOffset, TranslationTableRule *newRule,
		TranslationTableHeader *table) {
	/* direction = 1, newRule->dotslen > 1 */
	TranslationTableRule *currentRule = NULL;
	TranslationTableOffset *currentOffsetPtr =
			&table->backRules[_lou_stringHash(cells, 0, NULL)];
	if (newRule->opcode == CTO_SwapCc) return;
	while (*currentOffsetPtr) {
		int currentLength;
		int newLength;
		currentRule = (TranslationTableRule *)&table->ruleArea[*currentOffsetPtr];
		currentLength = currentRule->dotslen + currentRule->charslen;
		newLength = count + newRule->charslen;
		if (newLength > currentLength) break;
		if (currentLength == newLength)
			if ((currentRule->opcode == CTO_Always) && (newRule->opcode != CTO_Always))
				break;
		currentOffsetPtr = &currentRule->dotsnext;
	}
	newRule->dotsnext = *currentOffsetPtr;
	*currentOffsetPtr = newRuleOffset;
}

static int
addForwardPassRule(TranslationTableOffset newRuleOffset, TranslationTableRule *newRule,
		TranslationTableHeader *table) {
	TranslationTableOffset *currentOffsetPtr;
	TranslationTableRule *currentRule;
	switch (newRule->opcode) {
	case CTO_Correct:
		currentOffsetPtr = &table->forPassRules[0];
		break;
	case CTO_Context:
		currentOffsetPtr = &table->forPassRules[1];
		break;
	case CTO_Pass2:
		currentOffsetPtr = &table->forPassRules[2];
		break;
	case CTO_Pass3:
		currentOffsetPtr = &table->forPassRules[3];
		break;
	case CTO_Pass4:
		currentOffsetPtr = &table->forPassRules[4];
		break;
	default:
		return 0;
	}
	while (*currentOffsetPtr) {
		currentRule = (TranslationTableRule *)&table->ruleArea[*currentOffsetPtr];
		if (newRule->charslen > currentRule->charslen) break;
		currentOffsetPtr = &currentRule->charsnext;
	}
	newRule->charsnext = *currentOffsetPtr;
	*currentOffsetPtr = newRuleOffset;
	return 1;
}

static int
addBackwardPassRule(TranslationTableOffset newRuleOffset, TranslationTableRule *newRule,
		TranslationTableHeader *table) {
	TranslationTableOffset *currentOffsetPtr;
	TranslationTableRule *currentRule;
	switch (newRule->opcode) {
	case CTO_Correct:
		currentOffsetPtr = &table->backPassRules[0];
		break;
	case CTO_Context:
		currentOffsetPtr = &table->backPassRules[1];
		break;
	case CTO_Pass2:
		currentOffsetPtr = &table->backPassRules[2];
		break;
	case CTO_Pass3:
		currentOffsetPtr = &table->backPassRules[3];
		break;
	case CTO_Pass4:
		currentOffsetPtr = &table->backPassRules[4];
		break;
	default:
		return 0;
	}
	while (*currentOffsetPtr) {
		currentRule = (TranslationTableRule *)&table->ruleArea[*currentOffsetPtr];
		if (newRule->charslen > currentRule->charslen) break;
		currentOffsetPtr = &currentRule->dotsnext;
	}
	newRule->dotsnext = *currentOffsetPtr;
	*currentOffsetPtr = newRuleOffset;
	return 1;
}

static int
addRule(FileInfo *nested, TranslationTableOpcode opcode, CharsString *ruleChars,
		CharsString *ruleDots, TranslationTableCharacterAttributes after,
		TranslationTableCharacterAttributes before, TranslationTableOffset *newRuleOffset,
		TranslationTableRule **newRule, int noback, int nofor,
		TranslationTableHeader **table) {
	/* Add a rule to the table, using the hash function to find the start of
	 * chains and chaining both the chars and dots strings */
	TranslationTableOffset ruleOffset;
	int ruleSize = sizeof(TranslationTableRule) - (DEFAULTRULESIZE * CHARSIZE);
	if (ruleChars) ruleSize += CHARSIZE * ruleChars->length;
	if (ruleDots) ruleSize += CHARSIZE * ruleDots->length;
	if (!allocateSpaceInTranslationTable(nested, &ruleOffset, ruleSize, table)) return 0;
	TranslationTableRule *rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];
	if (newRule) *newRule = rule;
	if (newRuleOffset) *newRuleOffset = ruleOffset;
	rule->opcode = opcode;
	rule->after = after;
	rule->before = before;
	rule->nocross = 0;
	if (ruleChars)
		memcpy(&rule->charsdots[0], &ruleChars->chars[0],
				CHARSIZE * (rule->charslen = ruleChars->length));
	else
		rule->charslen = 0;
	if (ruleDots)
		memcpy(&rule->charsdots[rule->charslen], &ruleDots->chars[0],
				CHARSIZE * (rule->dotslen = ruleDots->length));
	else
		rule->dotslen = 0;

	/* link new rule into table. */
	if (opcode == CTO_SwapCc || opcode == CTO_SwapCd || opcode == CTO_SwapDd) return 1;
	if (opcode >= CTO_Context && opcode <= CTO_Pass4)
		if (!(opcode == CTO_Context && rule->charslen > 0)) {
			if (!nofor)
				if (!addForwardPassRule(ruleOffset, rule, *table)) return 0;
			if (!noback)
				if (!addBackwardPassRule(ruleOffset, rule, *table)) return 0;
			return 1;
		}
	if (!nofor) {
		if (rule->charslen == 1)
			addForwardRuleWithSingleChar(nested, ruleOffset, rule, table);
		else if (rule->charslen > 1)
			addForwardRuleWithMultipleChars(ruleOffset, rule, *table);
	}
	if (!noback) {
		widechar *cells;
		int count;

		if (rule->opcode == CTO_Context) {
			cells = &rule->charsdots[0];
			count = rule->charslen;
		} else {
			cells = &rule->charsdots[rule->charslen];
			count = rule->dotslen;
		}

		if (count == 1)
			addBackwardRuleWithSingleCell(nested, *cells, ruleOffset, rule, table);
		else if (count > 1)
			addBackwardRuleWithMultipleCells(cells, count, ruleOffset, rule, *table);
	}
	return 1;
}

static const CharacterClass *
findCharacterClass(const CharsString *name, const TranslationTableHeader *table) {
	/* Find a character class, whether predefined or user-defined */
	const CharacterClass *class = table->characterClasses;
	while (class) {
		if ((name->length == class->length) &&
				(memcmp(&name->chars[0], class->name, CHARSIZE * name->length) == 0))
			return class;
		class = class->next;
	}
	return NULL;
}

static TranslationTableCharacterAttributes
getNextNumberedAttribute(TranslationTableHeader *table) {
	/* Get the next attribute value for numbered attributes, or 0 if there is no more
	 * space in the table. */
	TranslationTableCharacterAttributes next = table->nextNumberedCharacterClassAttribute;
	if (next > CTC_UserDefined8) return 0;
	table->nextNumberedCharacterClassAttribute <<= 1;
	return next;
}

static TranslationTableCharacterAttributes
getNextAttribute(TranslationTableHeader *table) {
	/* Get the next attribute value, or 0 if there is no more space in the table. */
	TranslationTableCharacterAttributes next = table->nextCharacterClassAttribute;
	if (next) {
		if (next == CTC_LitDigit)
			table->nextCharacterClassAttribute = CTC_UserDefined9;
		else
			table->nextCharacterClassAttribute <<= 1;
		return next;
	} else
		return getNextNumberedAttribute(table);
}

static CharacterClass *
addCharacterClass(FileInfo *nested, const widechar *name, int length,
		TranslationTableHeader *table) {
	/* Define a character class, Whether predefined or user-defined */
	CharacterClass **classes = &table->characterClasses;
	TranslationTableCharacterAttributes attribute = getNextAttribute(table);
	CharacterClass *class;
	if (attribute) {
		if (!(class = malloc(sizeof(*class) + CHARSIZE * (length - 1))))
			_lou_outOfMemory();
		else {
			memset(class, 0, sizeof(*class));
			memcpy(class->name, name, CHARSIZE * (class->length = length));
			class->attribute = attribute;
			class->next = *classes;
			*classes = class;
			return class;
		}
	}
	compileError(nested, "character class table overflow.");
	return NULL;
}

static void
deallocateCharacterClasses(TranslationTableHeader *table) {
	CharacterClass **classes = &table->characterClasses;
	while (*classes) {
		CharacterClass *class = *classes;
		*classes = (*classes)->next;
		if (class) free(class);
	}
}

static int
allocateCharacterClasses(TranslationTableHeader *table) {
	/* Allocate memory for predefined character classes */
	int k = 0;
	table->characterClasses = NULL;
	table->nextCharacterClassAttribute = 1;  // CTC_Space
	table->nextNumberedCharacterClassAttribute = CTC_UserDefined1;
	while (characterClassNames[k]) {
		widechar wname[MAXSTRING];
		int length = (int)strlen(characterClassNames[k]);
		int kk;
		for (kk = 0; kk < length; kk++) wname[kk] = (widechar)characterClassNames[k][kk];
		if (!addCharacterClass(NULL, wname, length, table)) {
			deallocateCharacterClasses(table);
			return 0;
		}
		k++;
	}
	return 1;
}

static TranslationTableOpcode
getOpcode(FileInfo *nested, const CharsString *token) {
	static TranslationTableOpcode lastOpcode = 0;
	TranslationTableOpcode opcode = lastOpcode;

	do {
		if (token->length == opcodeLengths[opcode])
			if (eqasc2uni((unsigned char *)opcodeNames[opcode], &token->chars[0],
						token->length)) {
				lastOpcode = opcode;
				return opcode;
			}
		opcode++;
		if (opcode >= CTO_None) opcode = 0;
	} while (opcode != lastOpcode);
	compileError(nested, "opcode %s not defined.",
			_lou_showString(&token->chars[0], token->length, 0));
	return CTO_None;
}

TranslationTableOpcode EXPORT_CALL
_lou_findOpcodeNumber(const char *toFind) {
	/* Used by tools such as lou_debug */
	static TranslationTableOpcode lastOpcode = 0;
	TranslationTableOpcode opcode = lastOpcode;
	int length = (int)strlen(toFind);
	do {
		if (length == opcodeLengths[opcode] &&
				strcasecmp(toFind, opcodeNames[opcode]) == 0) {
			lastOpcode = opcode;
			return opcode;
		}
		opcode++;
		if (opcode >= CTO_None) opcode = 0;
	} while (opcode != lastOpcode);
	return CTO_None;
}

const char *EXPORT_CALL
_lou_findOpcodeName(TranslationTableOpcode opcode) {
	static char scratchBuf[MAXSTRING];
	/* Used by tools such as lou_debug */
	if (opcode < 0 || opcode >= CTO_None) {
		sprintf(scratchBuf, "%u", opcode);
		return scratchBuf;
	}
	return opcodeNames[opcode];
}

static widechar
hexValue(FileInfo *nested, const widechar *digits, int length) {
	int k;
	unsigned int binaryValue = 0;
	for (k = 0; k < length; k++) {
		unsigned int hexDigit = 0;
		if (digits[k] >= '0' && digits[k] <= '9')
			hexDigit = digits[k] - '0';
		else if (digits[k] >= 'a' && digits[k] <= 'f')
			hexDigit = digits[k] - 'a' + 10;
		else if (digits[k] >= 'A' && digits[k] <= 'F')
			hexDigit = digits[k] - 'A' + 10;
		else {
			compileError(nested, "invalid %d-digit hexadecimal number", length);
			return (widechar)0xffffffff;
		}
		binaryValue |= hexDigit << (4 * (length - 1 - k));
	}
	return (widechar)binaryValue;
}

#define MAXBYTES 7
static const unsigned int first0Bit[MAXBYTES] = { 0x80, 0xC0, 0xE0, 0xF0, 0xF8, 0xFC,
	0XFE };

static int
parseChars(FileInfo *nested, CharsString *result, CharsString *token) {
	int in = 0;
	int out = 0;
	int lastOutSize = 0;
	int lastIn;
	unsigned int ch = 0;
	int numBytes = 0;
	unsigned int utf32 = 0;
	int k;
	while (in < token->length) {
		ch = token->chars[in++] & 0xff;
		if (ch < 128) {
			if (ch == '\\') { /* escape sequence */
				switch (ch = token->chars[in]) {
				case '\\':
					break;
				case 'e':
					ch = 0x1b;
					break;
				case 'f':
					ch = 12;
					break;
				case 'n':
					ch = 10;
					break;
				case 'r':
					ch = 13;
					break;
				case 's':
					ch = ' ';
					break;
				case 't':
					ch = 9;
					break;
				case 'v':
					ch = 11;
					break;
				case 'w':
					ch = LOU_ENDSEGMENT;
					break;
				case 34:
					ch = QUOTESUB;
					break;
				case 'X':
					compileWarning(nested, "\\Xhhhh (with a capital 'X') is deprecated.");
				case 'x':
					if (token->length - in > 4) {
						ch = hexValue(nested, &token->chars[in + 1], 4);
						in += 4;
					}
					break;
				case 'Y':
					compileWarning(
							nested, "\\Yhhhhh (with a capital 'Y') is deprecated.");
				case 'y':
					if (CHARSIZE == 2) {
					not32:
						compileError(nested,
								"liblouis has not been compiled for 32-bit Unicode");
						break;
					}
					if (token->length - in > 5) {
						ch = hexValue(nested, &token->chars[in + 1], 5);
						in += 5;
					}
					break;
				case 'Z':
					compileWarning(
							nested, "\\Zhhhhhhhh (with a capital 'Z') is deprecated.");
				case 'z':
					if (CHARSIZE == 2) goto not32;
					if (token->length - in > 8) {
						ch = hexValue(nested, &token->chars[in + 1], 8);
						in += 8;
					}
					break;
				default:
					compileError(nested, "invalid escape sequence '\\%c'", ch);
					break;
				}
				in++;
			}
			if (out >= MAXSTRING - 1) {
				compileError(nested, "Token too long");
				result->length = MAXSTRING - 1;
				return 1;
			}
			result->chars[out++] = (widechar)ch;
			continue;
		}
		lastOutSize = out;
		lastIn = in;
		for (numBytes = MAXBYTES - 1; numBytes > 0; numBytes--)
			if (ch >= first0Bit[numBytes]) break;
		utf32 = ch & (0XFF - first0Bit[numBytes]);
		for (k = 0; k < numBytes; k++) {
			if (in >= MAXSTRING - 1) break;
			if (out >= MAXSTRING - 1) {
				compileError(nested, "Token too long");
				result->length = lastOutSize;
				return 1;
			}
			if (token->chars[in] < 128 || (token->chars[in] & 0x0040)) {
				compileWarning(nested, "invalid UTF-8. Assuming Latin-1.");
				result->chars[out++] = token->chars[lastIn];
				in = lastIn + 1;
				continue;
			}
			utf32 = (utf32 << 6) + (token->chars[in++] & 0x3f);
		}
		if (out >= MAXSTRING - 1) {
			compileError(nested, "Token too long");
			result->length = lastOutSize;
			return 1;
		}
		if (CHARSIZE == 2 && utf32 > 0xffff) utf32 = 0xffff;
		result->chars[out++] = (widechar)utf32;
	}
	result->length = out;
	return 1;
}

int EXPORT_CALL
_lou_extParseChars(const char *inString, widechar *outString) {
	/* Parse external character strings */
	CharsString wideIn;
	CharsString result;
	int k;
	for (k = 0; inString[k] && k < MAXSTRING - 1; k++) wideIn.chars[k] = inString[k];
	wideIn.chars[k] = 0;
	wideIn.length = k;
	parseChars(NULL, &result, &wideIn);
	if (errorCount) {
		errorCount = 0;
		return 0;
	}
	for (k = 0; k < result.length; k++) outString[k] = result.chars[k];
	return result.length;
}

static int
parseDots(FileInfo *nested, CharsString *cells, const CharsString *token) {
	/* get dot patterns */
	widechar cell = 0; /* assembly place for dots */
	int cellCount = 0;
	int index;
	int start = 0;

	for (index = 0; index < token->length; index++) {
		int started = index != start;
		widechar character = token->chars[index];
		switch (character) { /* or dots to make up Braille cell */
			{
				int dot;
			case '1':
				dot = LOU_DOT_1;
				goto haveDot;
			case '2':
				dot = LOU_DOT_2;
				goto haveDot;
			case '3':
				dot = LOU_DOT_3;
				goto haveDot;
			case '4':
				dot = LOU_DOT_4;
				goto haveDot;
			case '5':
				dot = LOU_DOT_5;
				goto haveDot;
			case '6':
				dot = LOU_DOT_6;
				goto haveDot;
			case '7':
				dot = LOU_DOT_7;
				goto haveDot;
			case '8':
				dot = LOU_DOT_8;
				goto haveDot;
			case '9':
				dot = LOU_DOT_9;
				goto haveDot;
			case 'a':
			case 'A':
				dot = LOU_DOT_10;
				goto haveDot;
			case 'b':
			case 'B':
				dot = LOU_DOT_11;
				goto haveDot;
			case 'c':
			case 'C':
				dot = LOU_DOT_12;
				goto haveDot;
			case 'd':
			case 'D':
				dot = LOU_DOT_13;
				goto haveDot;
			case 'e':
			case 'E':
				dot = LOU_DOT_14;
				goto haveDot;
			case 'f':
			case 'F':
				dot = LOU_DOT_15;
			haveDot:
				if (started && !cell) goto invalid;
				if (cell & dot) {
					compileError(nested, "dot specified more than once.");
					return 0;
				}
				cell |= dot;
				break;
			}
		case '0': /* blank */
			if (started) goto invalid;
			break;
		case '-': /* got all dots for this cell */
			if (!started) {
				compileError(nested, "missing cell specification.");
				return 0;
			}
			cells->chars[cellCount++] = cell | LOU_DOTS;
			cell = 0;
			start = index + 1;
			break;
		default:
		invalid:
			compileError(
					nested, "invalid dot number %s.", _lou_showString(&character, 1, 0));
			return 0;
		}
	}
	if (index == start) {
		compileError(nested, "missing cell specification.");
		return 0;
	}
	cells->chars[cellCount++] = cell | LOU_DOTS; /* last cell */
	cells->length = cellCount;
	return 1;
}

int EXPORT_CALL
_lou_extParseDots(const char *inString, widechar *outString) {
	/* Parse external dot patterns */
	CharsString wideIn;
	CharsString result;
	int k;
	for (k = 0; inString[k] && k < MAXSTRING - 1; k++) wideIn.chars[k] = inString[k];
	wideIn.chars[k] = 0;
	wideIn.length = k;
	parseDots(NULL, &result, &wideIn);
	if (errorCount) {
		errorCount = 0;
		return 0;
	}
	for (k = 0; k < result.length; k++) outString[k] = result.chars[k];
	outString[k] = 0;
	return result.length;
}

static int
getCharacters(FileInfo *nested, CharsString *characters, int *lastToken) {
	/* Get ruleChars string */
	CharsString token;
	if (getToken(nested, &token, "characters", lastToken))
		if (parseChars(nested, characters, &token)) return 1;
	return 0;
}

static int
getRuleCharsText(FileInfo *nested, CharsString *ruleChars, int *lastToken) {
	CharsString token;
	if (getToken(nested, &token, "Characters operand", lastToken))
		if (parseChars(nested, ruleChars, &token)) return 1;
	return 0;
}

static int
getRuleDotsText(FileInfo *nested, CharsString *ruleDots, int *lastToken) {
	CharsString token;
	if (getToken(nested, &token, "characters", lastToken))
		if (parseChars(nested, ruleDots, &token)) return 1;
	return 0;
}

static int
getRuleDotsPattern(FileInfo *nested, CharsString *ruleDots, int *lastToken) {
	/* Interpret the dets operand */
	CharsString token;
	if (getToken(nested, &token, "Dots operand", lastToken)) {
		if (token.length == 1 && token.chars[0] == '=') {
			ruleDots->length = 0;
			return 1;
		}
		if (parseDots(nested, ruleDots, &token)) return 1;
	}
	return 0;
}

static int
includeFile(FileInfo *nested, CharsString *includedFile, TranslationTableHeader **table,
		DisplayTableHeader **displayTable);

static TranslationTableOffset
findRuleName(const CharsString *name, const TranslationTableHeader *table) {
	const RuleName *nameRule = table->ruleNames;
	while (nameRule) {
		if ((name->length == nameRule->length) &&
				(memcmp(&name->chars[0], nameRule->name, CHARSIZE * name->length) == 0))
			return nameRule->ruleOffset;
		nameRule = nameRule->next;
	}
	return 0;
}

static int
addRuleName(FileInfo *nested, CharsString *name, TranslationTableOffset newRuleOffset,
		TranslationTableHeader *table) {
	int k;
	RuleName *nameRule;
	if (!(nameRule = malloc(sizeof(*nameRule) + CHARSIZE * (name->length - 1)))) {
		compileError(nested, "not enough memory");
		_lou_outOfMemory();
	}
	memset(nameRule, 0, sizeof(*nameRule));
	// a name is a sequence of characters in the ranges 'a'..'z' and 'A'..'Z'
	for (k = 0; k < name->length; k++) {
		widechar c = name->chars[k];
		if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z'))
			nameRule->name[k] = c;
		else {
			compileError(nested, "a name may contain only letters");
			free(nameRule);
			return 0;
		}
	}
	nameRule->length = name->length;
	nameRule->ruleOffset = newRuleOffset;
	nameRule->next = table->ruleNames;
	table->ruleNames = nameRule;
	return 1;
}

static void
deallocateRuleNames(TranslationTableHeader *table) {
	RuleName **ruleNames = &table->ruleNames;
	while (*ruleNames) {
		RuleName *nameRule = *ruleNames;
		*ruleNames = nameRule->next;
		if (nameRule) free(nameRule);
	}
}

static int
compileSwapDots(FileInfo *nested, CharsString *source, CharsString *dest) {
	int k = 0;
	int kk = 0;
	CharsString dotsSource;
	CharsString dotsDest;
	dest->length = 0;
	dotsSource.length = 0;
	while (k <= source->length) {
		if (source->chars[k] != ',' && k != source->length)
			dotsSource.chars[dotsSource.length++] = source->chars[k];
		else {
			if (!parseDots(nested, &dotsDest, &dotsSource)) return 0;
			dest->chars[dest->length++] = dotsDest.length + 1;
			for (kk = 0; kk < dotsDest.length; kk++)
				dest->chars[dest->length++] = dotsDest.chars[kk];
			dotsSource.length = 0;
		}
		k++;
	}
	return 1;
}

static int
compileSwap(FileInfo *nested, TranslationTableOpcode opcode, int *lastToken,
		TranslationTableOffset *newRuleOffset, TranslationTableRule **newRule, int noback,
		int nofor, TranslationTableHeader **table) {
	CharsString ruleChars;
	CharsString ruleDots;
	CharsString name;
	CharsString matches;
	CharsString replacements;
	TranslationTableOffset ruleOffset;
	if (!getToken(nested, &name, "name operand", lastToken)) return 0;
	if (!getToken(nested, &matches, "matches operand", lastToken)) return 0;
	if (!getToken(nested, &replacements, "replacements operand", lastToken)) return 0;
	if (opcode == CTO_SwapCc || opcode == CTO_SwapCd) {
		if (!parseChars(nested, &ruleChars, &matches)) return 0;
	} else {
		if (!compileSwapDots(nested, &matches, &ruleChars)) return 0;
	}
	if (opcode == CTO_SwapCc) {
		if (!parseChars(nested, &ruleDots, &replacements)) return 0;
	} else {
		if (!compileSwapDots(nested, &replacements, &ruleDots)) return 0;
	}
	if (!addRule(nested, opcode, &ruleChars, &ruleDots, 0, 0, &ruleOffset, newRule,
				noback, nofor, table))
		return 0;
	if (!addRuleName(nested, &name, ruleOffset, *table)) return 0;
	if (newRuleOffset) *newRuleOffset = ruleOffset;
	return 1;
}

static int
getNumber(widechar *source, widechar *dest) {
	/* Convert a string of wide character digits to an integer */
	int k = 0;
	*dest = 0;
	while (source[k] >= '0' && source[k] <= '9') *dest = 10 * *dest + (source[k++] - '0');
	return k;
}

/* Start of multipass compiler */

static int
passGetAttributes(CharsString *passLine, int *passLinepos,
		TranslationTableCharacterAttributes *passAttributes, FileInfo *passNested) {
	int more = 1;
	*passAttributes = 0;
	while (more) {
		switch (passLine->chars[*passLinepos]) {
		case pass_any:
			*passAttributes = 0xffffffff;
			break;
		case pass_digit:
			*passAttributes |= CTC_Digit;
			break;
		case pass_litDigit:
			*passAttributes |= CTC_LitDigit;
			break;
		case pass_letter:
			*passAttributes |= CTC_Letter;
			break;
		case pass_math:
			*passAttributes |= CTC_Math;
			break;
		case pass_punctuation:
			*passAttributes |= CTC_Punctuation;
			break;
		case pass_sign:
			*passAttributes |= CTC_Sign;
			break;
		case pass_space:
			*passAttributes |= CTC_Space;
			break;
		case pass_uppercase:
			*passAttributes |= CTC_UpperCase;
			break;
		case pass_lowercase:
			*passAttributes |= CTC_LowerCase;
			break;
		case pass_class1:
			*passAttributes |= CTC_UserDefined9;
			break;
		case pass_class2:
			*passAttributes |= CTC_UserDefined10;
			break;
		case pass_class3:
			*passAttributes |= CTC_UserDefined11;
			break;
		case pass_class4:
			*passAttributes |= CTC_UserDefined12;
			break;
		default:
			more = 0;
			break;
		}
		if (more) (*passLinepos)++;
	}
	if (!*passAttributes) {
		compileError(passNested, "missing attribute");
		(*passLinepos)--;
		return 0;
	}
	return 1;
}

static int
passGetDots(CharsString *passLine, int *passLinepos, CharsString *passHoldString,
		FileInfo *passNested) {
	CharsString collectDots;
	collectDots.length = 0;
	while (*passLinepos < passLine->length &&
			(passLine->chars[*passLinepos] == '-' ||
					(passLine->chars[*passLinepos] >= '0' &&
							passLine->chars[*passLinepos] <= '9') ||
					((passLine->chars[*passLinepos] | 32) >= 'a' &&
							(passLine->chars[*passLinepos] | 32) <= 'f')))
		collectDots.chars[collectDots.length++] = passLine->chars[(*passLinepos)++];
	if (!parseDots(passNested, passHoldString, &collectDots)) return 0;
	return 1;
}

static int
passGetString(CharsString *passLine, int *passLinepos, CharsString *passHoldString,
		FileInfo *passNested) {
	passHoldString->length = 0;
	while (1) {
		if ((*passLinepos >= passLine->length) || !passLine->chars[*passLinepos]) {
			compileError(passNested, "unterminated string");
			return 0;
		}
		if (passLine->chars[*passLinepos] == 34) break;
		if (passLine->chars[*passLinepos] == QUOTESUB)
			passHoldString->chars[passHoldString->length++] = 34;
		else
			passHoldString->chars[passHoldString->length++] =
					passLine->chars[*passLinepos];
		(*passLinepos)++;
	}
	passHoldString->chars[passHoldString->length] = 0;
	(*passLinepos)++;
	return 1;
}

static int
passGetNumber(CharsString *passLine, int *passLinepos, widechar *passHoldNumber) {
	/* Convert a string of wide character digits to an integer */
	*passHoldNumber = 0;
	while ((*passLinepos < passLine->length) && (passLine->chars[*passLinepos] >= '0') &&
			(passLine->chars[*passLinepos] <= '9'))
		*passHoldNumber =
				10 * (*passHoldNumber) + (passLine->chars[(*passLinepos)++] - '0');
	return 1;
}

static int
passGetVariableNumber(FileInfo *nested, CharsString *passLine, int *passLinepos,
		widechar *passHoldNumber) {
	if (!passGetNumber(passLine, passLinepos, passHoldNumber)) return 0;
	if ((*passHoldNumber >= 0) && (*passHoldNumber < NUMVAR)) return 1;
	compileError(nested, "variable number out of range");
	return 0;
}

static int
passGetName(CharsString *passLine, int *passLinepos, CharsString *passHoldString) {
	passHoldString->length = 0;
	// a name is a sequence of characters in the ranges 'a'..'z' and 'A'..'Z'
	do {
		widechar c = passLine->chars[*passLinepos];
		if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) {
			passHoldString->chars[passHoldString->length++] = c;
			(*passLinepos)++;
		} else {
			break;
		}
	} while (*passLinepos < passLine->length);
	return 1;
}

static inline int
wantsString(TranslationTableOpcode opcode, int actionPart, int nofor) {
	if (opcode == CTO_Correct) return 1;
	if (opcode != CTO_Context) return 0;
	return !nofor == !actionPart;
}

static int
verifyStringOrDots(FileInfo *nested, TranslationTableOpcode opcode, int isString,
		int actionPart, int nofor) {
	if (!wantsString(opcode, actionPart, nofor) == !isString) return 1;

	compileError(nested, "%s are not allowed in the %s part of a %s translation %s rule.",
			isString ? "strings" : "dots", getPartName(actionPart),
			nofor ? "backward" : "forward", _lou_findOpcodeName(opcode));

	return 0;
}

static int
compilePassOpcode(FileInfo *nested, TranslationTableOpcode opcode,
		TranslationTableOffset *newRuleOffset, TranslationTableRule **newRule, int noback,
		int nofor, TranslationTableHeader **table) {
	static CharsString passRuleChars;
	static CharsString passRuleDots;
	/* Compile the operands of a pass opcode */
	widechar passSubOp;
	const CharacterClass *class;
	TranslationTableOffset ruleOffset = 0;
	TranslationTableRule *rule = NULL;
	int k;
	int kk = 0;
	int endTest = 0;
	widechar *passInstructions = passRuleDots.chars;
	int passIC = 0; /* Instruction counter */
	passRuleChars.length = 0;
	FileInfo *passNested = nested;
	CharsString passHoldString;
	widechar passHoldNumber;
	CharsString passLine;
	int passLinepos = 0;
	TranslationTableCharacterAttributes passAttributes;
	passHoldString.length = 0;
	for (k = nested->linepos; k < nested->linelen; k++)
		passHoldString.chars[passHoldString.length++] = nested->line[k];
#define SEPCHAR 0x0001
	for (k = 0; k < passHoldString.length && passHoldString.chars[k] > 32; k++)
		;
	if (k < passHoldString.length)
		passHoldString.chars[k] = SEPCHAR;
	else {
		compileError(passNested, "Invalid multipass operands");
		return 0;
	}
	parseChars(passNested, &passLine, &passHoldString);
	/* Compile test part */
	for (k = 0; k < passLine.length && passLine.chars[k] != SEPCHAR; k++)
		;
	endTest = k;
	passLine.chars[endTest] = pass_endTest;
	passLinepos = 0;
	while (passLinepos <= endTest) {
		if (passIC >= MAXSTRING) {
			compileError(passNested, "Test part in multipass operand too long");
			return 0;
		}
		switch ((passSubOp = passLine.chars[passLinepos])) {
		case pass_lookback:
			passInstructions[passIC++] = pass_lookback;
			passLinepos++;
			passGetNumber(&passLine, &passLinepos, &passHoldNumber);
			if (passHoldNumber == 0) passHoldNumber = 1;
			passInstructions[passIC++] = passHoldNumber;
			break;
		case pass_not:
			passInstructions[passIC++] = pass_not;
			passLinepos++;
			break;
		case pass_first:
			passInstructions[passIC++] = pass_first;
			passLinepos++;
			break;
		case pass_last:
			passInstructions[passIC++] = pass_last;
			passLinepos++;
			break;
		case pass_search:
			passInstructions[passIC++] = pass_search;
			passLinepos++;
			break;
		case pass_string:
			if (!verifyStringOrDots(nested, opcode, 1, 0, nofor)) {
				return 0;
			}
			passLinepos++;
			passInstructions[passIC++] = pass_string;
			passGetString(&passLine, &passLinepos, &passHoldString, passNested);
			goto testDoCharsDots;
		case pass_dots:
			if (!verifyStringOrDots(nested, opcode, 0, 0, nofor)) {
				return 0;
			}
			passLinepos++;
			passInstructions[passIC++] = pass_dots;
			passGetDots(&passLine, &passLinepos, &passHoldString, passNested);
		testDoCharsDots:
			if (passHoldString.length == 0) return 0;
			if (passIC >= MAXSTRING) {
				compileError(passNested,
						"@ operand in test part of multipass operand too long");
				return 0;
			}
			passInstructions[passIC++] = passHoldString.length;
			for (kk = 0; kk < passHoldString.length; kk++) {
				if (passIC >= MAXSTRING) {
					compileError(passNested,
							"@ operand in test part of multipass operand too long");
					return 0;
				}
				passInstructions[passIC++] = passHoldString.chars[kk];
			}
			break;
		case pass_startReplace:
			passInstructions[passIC++] = pass_startReplace;
			passLinepos++;
			break;
		case pass_endReplace:
			passInstructions[passIC++] = pass_endReplace;
			passLinepos++;
			break;
		case pass_variable:
			passLinepos++;
			if (!passGetVariableNumber(nested, &passLine, &passLinepos, &passHoldNumber))
				return 0;
			switch (passLine.chars[passLinepos]) {
			case pass_eq:
				passInstructions[passIC++] = pass_eq;
				goto doComp;
			case pass_lt:
				if (passLine.chars[passLinepos + 1] == pass_eq) {
					passLinepos++;
					passInstructions[passIC++] = pass_lteq;
				} else
					passInstructions[passIC++] = pass_lt;
				goto doComp;
			case pass_gt:
				if (passLine.chars[passLinepos + 1] == pass_eq) {
					passLinepos++;
					passInstructions[passIC++] = pass_gteq;
				} else
					passInstructions[passIC++] = pass_gt;
			doComp:
				passInstructions[passIC++] = passHoldNumber;
				passLinepos++;
				passGetNumber(&passLine, &passLinepos, &passHoldNumber);
				passInstructions[passIC++] = passHoldNumber;
				break;
			default:
				compileError(passNested, "incorrect comparison operator");
				return 0;
			}
			break;
		case pass_attributes:
			passLinepos++;
			if (!passGetAttributes(&passLine, &passLinepos, &passAttributes, passNested))
				return 0;
		insertAttributes:
			passInstructions[passIC++] = pass_attributes;
			passInstructions[passIC++] = (passAttributes >> 48) & 0xffff;
			passInstructions[passIC++] = (passAttributes >> 32) & 0xffff;
			passInstructions[passIC++] = (passAttributes >> 16) & 0xffff;
			passInstructions[passIC++] = passAttributes & 0xffff;
		getRange:
			if (passLine.chars[passLinepos] == pass_until) {
				passLinepos++;
				passInstructions[passIC++] = 1;
				passInstructions[passIC++] = 0xffff;
				break;
			}
			passGetNumber(&passLine, &passLinepos, &passHoldNumber);
			if (passHoldNumber == 0) {
				passHoldNumber = passInstructions[passIC++] = 1;
				passInstructions[passIC++] = 1; /* This is not an error */
				break;
			}
			passInstructions[passIC++] = passHoldNumber;
			if (passLine.chars[passLinepos] != pass_hyphen) {
				passInstructions[passIC++] = passHoldNumber;
				break;
			}
			passLinepos++;
			passGetNumber(&passLine, &passLinepos, &passHoldNumber);
			if (passHoldNumber == 0) {
				compileError(passNested, "invalid range");
				return 0;
			}
			passInstructions[passIC++] = passHoldNumber;
			break;
		case pass_groupstart:
		case pass_groupend:
			passLinepos++;
			passGetName(&passLine, &passLinepos, &passHoldString);
			ruleOffset = findRuleName(&passHoldString, *table);
			if (ruleOffset)
				rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];
			if (rule && rule->opcode == CTO_Grouping) {
				passInstructions[passIC++] = passSubOp;
				passInstructions[passIC++] = ruleOffset >> 16;
				passInstructions[passIC++] = ruleOffset & 0xffff;
				break;
			} else {
				compileError(passNested, "%s is not a grouping name",
						_lou_showString(
								&passHoldString.chars[0], passHoldString.length, 0));
				return 0;
			}
			break;
		case pass_swap:
			passLinepos++;
			passGetName(&passLine, &passLinepos, &passHoldString);
			if ((class = findCharacterClass(&passHoldString, *table))) {
				passAttributes = class->attribute;
				goto insertAttributes;
			}
			ruleOffset = findRuleName(&passHoldString, *table);
			if (ruleOffset)
				rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];
			if (rule &&
					(rule->opcode == CTO_SwapCc || rule->opcode == CTO_SwapCd ||
							rule->opcode == CTO_SwapDd)) {
				passInstructions[passIC++] = pass_swap;
				passInstructions[passIC++] = ruleOffset >> 16;
				passInstructions[passIC++] = ruleOffset & 0xffff;
				goto getRange;
			}
			compileError(passNested, "%s is neither a class name nor a swap name.",
					_lou_showString(&passHoldString.chars[0], passHoldString.length, 0));
			return 0;
		case pass_endTest:
			passInstructions[passIC++] = pass_endTest;
			passLinepos++;
			break;
		default:
			compileError(passNested, "incorrect operator '%c ' in test part",
					passLine.chars[passLinepos]);
			return 0;
		}

	} /* Compile action part */

	/* Compile action part */
	while (passLinepos < passLine.length && passLine.chars[passLinepos] <= 32)
		passLinepos++;
	while (passLinepos < passLine.length && passLine.chars[passLinepos] > 32) {
		if (passIC >= MAXSTRING) {
			compileError(passNested, "Action part in multipass operand too long");
			return 0;
		}
		switch ((passSubOp = passLine.chars[passLinepos])) {
		case pass_string:
			if (!verifyStringOrDots(nested, opcode, 1, 1, nofor)) {
				return 0;
			}
			passLinepos++;
			passInstructions[passIC++] = pass_string;
			passGetString(&passLine, &passLinepos, &passHoldString, passNested);
			goto actionDoCharsDots;
		case pass_dots:
			if (!verifyStringOrDots(nested, opcode, 0, 1, nofor)) {
				return 0;
			}
			passLinepos++;
			passGetDots(&passLine, &passLinepos, &passHoldString, passNested);
			passInstructions[passIC++] = pass_dots;
		actionDoCharsDots:
			if (passHoldString.length == 0) return 0;
			if (passIC >= MAXSTRING) {
				compileError(passNested,
						"@ operand in action part of multipass operand too long");
				return 0;
			}
			passInstructions[passIC++] = passHoldString.length;
			for (kk = 0; kk < passHoldString.length; kk++) {
				if (passIC >= MAXSTRING) {
					compileError(passNested,
							"@ operand in action part of multipass operand too long");
					return 0;
				}
				passInstructions[passIC++] = passHoldString.chars[kk];
			}
			break;
		case pass_variable:
			passLinepos++;
			if (!passGetVariableNumber(nested, &passLine, &passLinepos, &passHoldNumber))
				return 0;
			switch (passLine.chars[passLinepos]) {
			case pass_eq:
				passInstructions[passIC++] = pass_eq;
				passInstructions[passIC++] = passHoldNumber;
				passLinepos++;
				passGetNumber(&passLine, &passLinepos, &passHoldNumber);
				passInstructions[passIC++] = passHoldNumber;
				break;
			case pass_plus:
			case pass_hyphen:
				passInstructions[passIC++] = passLine.chars[passLinepos++];
				passInstructions[passIC++] = passHoldNumber;
				break;
			default:
				compileError(passNested, "incorrect variable operator in action part");
				return 0;
			}
			break;
		case pass_copy:
			passInstructions[passIC++] = pass_copy;
			passLinepos++;
			break;
		case pass_omit:
			passInstructions[passIC++] = pass_omit;
			passLinepos++;
			break;
		case pass_groupreplace:
		case pass_groupstart:
		case pass_groupend:
			passLinepos++;
			passGetName(&passLine, &passLinepos, &passHoldString);
			ruleOffset = findRuleName(&passHoldString, *table);
			if (ruleOffset)
				rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];
			if (rule && rule->opcode == CTO_Grouping) {
				passInstructions[passIC++] = passSubOp;
				passInstructions[passIC++] = ruleOffset >> 16;
				passInstructions[passIC++] = ruleOffset & 0xffff;
				break;
			}
			compileError(passNested, "%s is not a grouping name",
					_lou_showString(&passHoldString.chars[0], passHoldString.length, 0));
			return 0;
		case pass_swap:
			passLinepos++;
			passGetName(&passLine, &passLinepos, &passHoldString);
			ruleOffset = findRuleName(&passHoldString, *table);
			if (ruleOffset)
				rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];
			if (rule &&
					(rule->opcode == CTO_SwapCc || rule->opcode == CTO_SwapCd ||
							rule->opcode == CTO_SwapDd)) {
				passInstructions[passIC++] = pass_swap;
				passInstructions[passIC++] = ruleOffset >> 16;
				passInstructions[passIC++] = ruleOffset & 0xffff;
				break;
			}
			compileError(passNested, "%s is not a swap name.",
					_lou_showString(&passHoldString.chars[0], passHoldString.length, 0));
			return 0;
			break;
		default:
			compileError(passNested, "incorrect operator in action part");
			return 0;
		}
	}

	/* Analyze and add rule */
	passRuleDots.length = passIC;

	{
		widechar *characters;
		int length;
		int found = passFindCharacters(
				passNested, passInstructions, passRuleDots.length, &characters, &length);

		if (!found) return 0;

		if (characters) {
			for (k = 0; k < length; k += 1) passRuleChars.chars[k] = characters[k];
			passRuleChars.length = k;
		}
	}

	if (!addRule(passNested, opcode, &passRuleChars, &passRuleDots, 0, 0, newRuleOffset,
				newRule, noback, nofor, table))
		return 0;
	return 1;
}

/* End of multipass compiler */

static int
compileBrailleIndicator(FileInfo *nested, const char *ermsg,
		TranslationTableOpcode opcode, int *lastToken,
		TranslationTableOffset *newRuleOffset, TranslationTableRule **newRule, int noback,
		int nofor, TranslationTableHeader **table) {
	CharsString token;
	CharsString cells;
	if (getToken(nested, &token, ermsg, lastToken))
		if (parseDots(nested, &cells, &token))
			if (!addRule(nested, opcode, NULL, &cells, 0, 0, newRuleOffset, newRule,
						noback, nofor, table))
				return 0;
	return 1;
}

static int
compileNumber(FileInfo *nested, int *lastToken) {
	CharsString token;
	widechar dest;
	if (!getToken(nested, &token, "number", lastToken)) return 0;
	getNumber(&token.chars[0], &dest);
	if (!(dest > 0)) {
		compileError(nested, "a nonzero positive number is required");
		return 0;
	}
	return dest;
}

static int
compileGrouping(FileInfo *nested, int *lastToken, TranslationTableOffset *newRuleOffset,
		TranslationTableRule **newRule, int noback, int nofor,
		TranslationTableHeader **table, DisplayTableHeader **displayTable) {
	int k;
	CharsString name;
	CharsString groupChars;
	CharsString groupDots;
	CharsString dotsParsed;
	if (!getToken(nested, &name, "name operand", lastToken)) return 0;
	if (!getRuleCharsText(nested, &groupChars, lastToken)) return 0;
	if (!getToken(nested, &groupDots, "dots operand", lastToken)) return 0;
	for (k = 0; k < groupDots.length && groupDots.chars[k] != ','; k++)
		;
	if (k == groupDots.length) {
		compileError(
				nested, "Dots operand must consist of two cells separated by a comma");
		return 0;
	}
	groupDots.chars[k] = '-';
	if (!parseDots(nested, &dotsParsed, &groupDots)) return 0;
	if (groupChars.length != 2 || dotsParsed.length != 2) {
		compileError(nested,
				"two Unicode characters and two cells separated by a comma are needed.");
		return 0;
	}
	if (table) {
		TranslationTableOffset ruleOffset;
		TranslationTableCharacter *charsDotsPtr;
		charsDotsPtr = putChar(nested, groupChars.chars[0], table);
		charsDotsPtr->attributes |= CTC_Math;
		charsDotsPtr->uppercase = charsDotsPtr->realchar;
		charsDotsPtr->lowercase = charsDotsPtr->realchar;
		charsDotsPtr = putChar(nested, groupChars.chars[1], table);
		charsDotsPtr->attributes |= CTC_Math;
		charsDotsPtr->uppercase = charsDotsPtr->realchar;
		charsDotsPtr->lowercase = charsDotsPtr->realchar;
		charsDotsPtr = putDots(nested, dotsParsed.chars[0], table);
		charsDotsPtr->attributes |= CTC_Math;
		charsDotsPtr->uppercase = charsDotsPtr->realchar;
		charsDotsPtr->lowercase = charsDotsPtr->realchar;
		charsDotsPtr = putDots(nested, dotsParsed.chars[1], table);
		charsDotsPtr->attributes |= CTC_Math;
		charsDotsPtr->uppercase = charsDotsPtr->realchar;
		charsDotsPtr->lowercase = charsDotsPtr->realchar;
		if (!addRule(nested, CTO_Grouping, &groupChars, &dotsParsed, 0, 0, &ruleOffset,
					newRule, noback, nofor, table))
			return 0;
		if (!addRuleName(nested, &name, ruleOffset, *table)) return 0;
		if (newRuleOffset) *newRuleOffset = ruleOffset;
	}
	if (displayTable) {
		putCharDotsMapping(
				nested, groupChars.chars[0], dotsParsed.chars[0], displayTable);
		putCharDotsMapping(
				nested, groupChars.chars[1], dotsParsed.chars[1], displayTable);
	}
	if (table) {
		widechar endChar;
		widechar endDots;
		endChar = groupChars.chars[1];
		endDots = dotsParsed.chars[1];
		groupChars.length = dotsParsed.length = 1;
		if (!addRule(nested, CTO_Math, &groupChars, &dotsParsed, 0, 0, newRuleOffset,
					newRule, noback, nofor, table))
			return 0;
		groupChars.chars[0] = endChar;
		dotsParsed.chars[0] = endDots;
		if (!addRule(nested, CTO_Math, &groupChars, &dotsParsed, 0, 0, newRuleOffset,
					newRule, noback, nofor, table))
			return 0;
	}
	return 1;
}

static int
compileUplow(FileInfo *nested, int *lastToken, TranslationTableOffset *newRuleOffset,
		TranslationTableRule **newRule, int noback, int nofor,
		TranslationTableHeader **table, DisplayTableHeader **displayTable) {
	int k;
	TranslationTableCharacter *upperChar;
	TranslationTableCharacter *lowerChar;
	TranslationTableCharacter *upperCell = NULL;
	TranslationTableCharacter *lowerCell = NULL;
	CharsString ruleChars;
	CharsString ruleDots;
	CharsString upperDots;
	CharsString lowerDots;
	int haveLowerDots = 0;
	TranslationTableCharacterAttributes attr;
	if (!getRuleCharsText(nested, &ruleChars, lastToken)) return 0;
	if (!getToken(nested, &ruleDots, "dots operand", lastToken)) return 0;
	for (k = 0; k < ruleDots.length && ruleDots.chars[k] != ','; k++)
		;
	if (k == ruleDots.length) {
		if (!parseDots(nested, &upperDots, &ruleDots)) return 0;
		lowerDots.length = upperDots.length;
		for (k = 0; k < upperDots.length; k++) lowerDots.chars[k] = upperDots.chars[k];
		lowerDots.chars[k] = 0;
	} else {
		haveLowerDots = ruleDots.length;
		ruleDots.length = k;
		if (!parseDots(nested, &upperDots, &ruleDots)) return 0;
		ruleDots.length = 0;
		k++;
		for (; k < haveLowerDots; k++)
			ruleDots.chars[ruleDots.length++] = ruleDots.chars[k];
		if (!parseDots(nested, &lowerDots, &ruleDots)) return 0;
	}
	if (ruleChars.length != 2 || upperDots.length < 1) {
		compileError(nested,
				"Exactly two Unicode characters and at least one cell are required.");
		return 0;
	}
	if (haveLowerDots && lowerDots.length < 1) {
		compileError(nested, "at least one cell is required after the comma.");
		return 0;
	}
	if (table) {
		upperChar = putChar(nested, ruleChars.chars[0], table);
		upperChar->attributes |= CTC_Letter | CTC_UpperCase;
		upperChar->uppercase = ruleChars.chars[0];
		upperChar->lowercase = ruleChars.chars[1];
		lowerChar = putChar(nested, ruleChars.chars[1], table);
		lowerChar->attributes |= CTC_Letter | CTC_LowerCase;
		lowerChar->uppercase = ruleChars.chars[0];
		lowerChar->lowercase = ruleChars.chars[1];
		for (k = 0; k < upperDots.length; k++)
			if (!getDots(upperDots.chars[k], *table)) {
				attr = CTC_Letter | CTC_UpperCase;
				upperCell = putDots(nested, upperDots.chars[k], table);
				upperCell->attributes |= attr;
				upperCell->uppercase = upperCell->realchar;
			}
		if (haveLowerDots) {
			for (k = 0; k < lowerDots.length; k++)
				if (!getDots(lowerDots.chars[k], *table)) {
					attr = CTC_Letter | CTC_LowerCase;
					lowerCell = putDots(nested, lowerDots.chars[k], table);
					if (lowerDots.length != 1) attr = CTC_Space;
					lowerCell->attributes |= attr;
					lowerCell->lowercase = lowerCell->realchar;
				}
		} else if (upperCell != NULL && upperDots.length == 1)
			upperCell->attributes |= CTC_LowerCase;
		if (upperCell != NULL) upperCell->lowercase = lowerDots.chars[0];
		if (lowerCell != NULL) lowerCell->uppercase = upperDots.chars[0];
	}
	if (displayTable) {
		if (lowerDots.length == 1)
			putCharDotsMapping(
					nested, ruleChars.chars[1], lowerDots.chars[0], displayTable);
		if (upperDots.length == 1)
			putCharDotsMapping(
					nested, ruleChars.chars[0], upperDots.chars[0], displayTable);
	}
	if (table) {
		ruleChars.length = 1;
		ruleChars.chars[2] = ruleChars.chars[0];
		ruleChars.chars[0] = ruleChars.chars[1];
		if (!addRule(nested, CTO_LowerCase, &ruleChars, &lowerDots, 0, 0, newRuleOffset,
					newRule, noback, nofor, table))
			return 0;
		ruleChars.chars[0] = ruleChars.chars[2];
		if (!addRule(nested, CTO_UpperCase, &ruleChars, &upperDots, 0, 0, newRuleOffset,
					newRule, noback, nofor, table))
			return 0;
	}
	return 1;
}

/* Functions for compiling hyphenation tables */

typedef struct HyphenDict { /* hyphenation dictionary: finite state machine */
	int numStates;
	HyphenationState *states;
} HyphenDict;

#define DEFAULTSTATE 0xffff
#define HYPHENHASHSIZE 8191

typedef struct HyphenHashEntry {
	struct HyphenHashEntry *next;
	CharsString *key;
	int val;
} HyphenHashEntry;

typedef struct HyphenHashTab {
	HyphenHashEntry *entries[HYPHENHASHSIZE];
} HyphenHashTab;

/* a hash function from ASU - adapted from Gtk+ */
static unsigned int
hyphenStringHash(const CharsString *s) {
	int k;
	unsigned int h = 0, g;
	for (k = 0; k < s->length; k++) {
		h = (h << 4) + s->chars[k];
		if ((g = h & 0xf0000000)) {
			h = h ^ (g >> 24);
			h = h ^ g;
		}
	}
	return h;
}

static HyphenHashTab *
hyphenHashNew(void) {
	HyphenHashTab *hashTab;
	if (!(hashTab = malloc(sizeof(HyphenHashTab)))) _lou_outOfMemory();
	memset(hashTab, 0, sizeof(HyphenHashTab));
	return hashTab;
}

static void
hyphenHashFree(HyphenHashTab *hashTab) {
	int i;
	HyphenHashEntry *e, *next;
	for (i = 0; i < HYPHENHASHSIZE; i++)
		for (e = hashTab->entries[i]; e; e = next) {
			next = e->next;
			free(e->key);
			free(e);
		}
	free(hashTab);
}

/* assumes that key is not already present! */
static void
hyphenHashInsert(HyphenHashTab *hashTab, const CharsString *key, int val) {
	int i, j;
	HyphenHashEntry *e;
	i = hyphenStringHash(key) % HYPHENHASHSIZE;
	if (!(e = malloc(sizeof(HyphenHashEntry)))) _lou_outOfMemory();
	e->next = hashTab->entries[i];
	e->key = malloc((key->length + 1) * CHARSIZE);
	if (!e->key) _lou_outOfMemory();
	e->key->length = key->length;
	for (j = 0; j < key->length; j++) e->key->chars[j] = key->chars[j];
	e->val = val;
	hashTab->entries[i] = e;
}

/* return val if found, otherwise DEFAULTSTATE */
static int
hyphenHashLookup(HyphenHashTab *hashTab, const CharsString *key) {
	int i, j;
	HyphenHashEntry *e;
	if (key->length == 0) return 0;
	i = hyphenStringHash(key) % HYPHENHASHSIZE;
	for (e = hashTab->entries[i]; e; e = e->next) {
		if (key->length != e->key->length) continue;
		for (j = 0; j < key->length; j++)
			if (key->chars[j] != e->key->chars[j]) break;
		if (j == key->length) return e->val;
	}
	return DEFAULTSTATE;
}

static int
hyphenGetNewState(HyphenDict *dict, HyphenHashTab *hashTab, const CharsString *string) {
	hyphenHashInsert(hashTab, string, dict->numStates);
	/* predicate is true if dict->numStates is a power of two */
	if (!(dict->numStates & (dict->numStates - 1)))
		dict->states =
				realloc(dict->states, (dict->numStates << 1) * sizeof(HyphenationState));
	if (!dict->states) _lou_outOfMemory();
	dict->states[dict->numStates].hyphenPattern = 0;
	dict->states[dict->numStates].fallbackState = DEFAULTSTATE;
	dict->states[dict->numStates].numTrans = 0;
	dict->states[dict->numStates].trans.pointer = NULL;
	return dict->numStates++;
}

/* add a transition from state1 to state2 through ch - assumes that the
 * transition does not already exist */
static void
hyphenAddTrans(HyphenDict *dict, int state1, int state2, widechar ch) {
	int numTrans;
	numTrans = dict->states[state1].numTrans;
	if (numTrans == 0)
		dict->states[state1].trans.pointer = malloc(sizeof(HyphenationTrans));
	else if (!(numTrans & (numTrans - 1)))
		dict->states[state1].trans.pointer = realloc(dict->states[state1].trans.pointer,
				(numTrans << 1) * sizeof(HyphenationTrans));
	dict->states[state1].trans.pointer[numTrans].ch = ch;
	dict->states[state1].trans.pointer[numTrans].newState = state2;
	dict->states[state1].numTrans++;
}

static int
compileHyphenation(FileInfo *nested, CharsString *encoding, int *lastToken,
		TranslationTableHeader **table) {
	CharsString hyph;
	HyphenationTrans *holdPointer;
	HyphenHashTab *hashTab;
	CharsString word;
	char pattern[MAXSTRING + 1];
	unsigned int stateNum = 0, lastState = 0;
	int i, j, k = encoding->length;
	widechar ch;
	int found;
	HyphenHashEntry *e;
	HyphenDict dict;
	TranslationTableOffset holdOffset;
	/* Set aside enough space for hyphenation states and transitions in
	 * translation table. Must be done before anything else */
	allocateSpaceInTranslationTable(nested, NULL, 250000, table);
	hashTab = hyphenHashNew();
	dict.numStates = 1;
	dict.states = malloc(sizeof(HyphenationState));
	if (!dict.states) _lou_outOfMemory();
	dict.states[0].hyphenPattern = 0;
	dict.states[0].fallbackState = DEFAULTSTATE;
	dict.states[0].numTrans = 0;
	dict.states[0].trans.pointer = NULL;
	do {
		if (encoding->chars[0] == 'I') {
			if (!getToken(nested, &hyph, NULL, lastToken)) continue;
		} else {
			/* UTF-8 */
			if (!getToken(nested, &word, NULL, lastToken)) continue;
			parseChars(nested, &hyph, &word);
		}
		if (hyph.length == 0 || hyph.chars[0] == '#' || hyph.chars[0] == '%' ||
				hyph.chars[0] == '<')
			continue; /* comment */
		j = 0;
		pattern[j] = '0';
		for (i = 0; i < hyph.length; i++) {
			if (hyph.chars[i] >= '0' && hyph.chars[i] <= '9')
				pattern[j] = (char)hyph.chars[i];
			else {
				word.chars[j] = hyph.chars[i];
				pattern[++j] = '0';
			}
		}
		word.chars[j] = 0;
		word.length = j;
		pattern[j + 1] = 0;
		for (i = 0; pattern[i] == '0'; i++)
			;
		found = hyphenHashLookup(hashTab, &word);
		if (found != DEFAULTSTATE)
			stateNum = found;
		else
			stateNum = hyphenGetNewState(&dict, hashTab, &word);
		k = j + 2 - i;
		if (k > 0) {
			allocateSpaceInTranslationTable(
					nested, &dict.states[stateNum].hyphenPattern, k, table);
			memcpy(&(*table)->ruleArea[dict.states[stateNum].hyphenPattern], &pattern[i],
					k);
		}
		/* now, put in the prefix transitions */
		while (found == DEFAULTSTATE) {
			lastState = stateNum;
			ch = word.chars[word.length-- - 1];
			found = hyphenHashLookup(hashTab, &word);
			if (found != DEFAULTSTATE)
				stateNum = found;
			else
				stateNum = hyphenGetNewState(&dict, hashTab, &word);
			hyphenAddTrans(&dict, stateNum, lastState, ch);
		}
	} while (_lou_getALine(nested));
	/* put in the fallback states */
	for (i = 0; i < HYPHENHASHSIZE; i++) {
		for (e = hashTab->entries[i]; e; e = e->next) {
			for (j = 1; j <= e->key->length; j++) {
				word.length = 0;
				for (k = j; k < e->key->length; k++)
					word.chars[word.length++] = e->key->chars[k];
				stateNum = hyphenHashLookup(hashTab, &word);
				if (stateNum != DEFAULTSTATE) break;
			}
			if (e->val) dict.states[e->val].fallbackState = stateNum;
		}
	}
	hyphenHashFree(hashTab);
	/* Transfer hyphenation information to table */
	for (i = 0; i < dict.numStates; i++) {
		if (dict.states[i].numTrans == 0)
			dict.states[i].trans.offset = 0;
		else {
			holdPointer = dict.states[i].trans.pointer;
			allocateSpaceInTranslationTable(nested, &dict.states[i].trans.offset,
					dict.states[i].numTrans * sizeof(HyphenationTrans), table);
			memcpy(&(*table)->ruleArea[dict.states[i].trans.offset], holdPointer,
					dict.states[i].numTrans * sizeof(HyphenationTrans));
			free(holdPointer);
		}
	}
	allocateSpaceInTranslationTable(
			nested, &holdOffset, dict.numStates * sizeof(HyphenationState), table);
	(*table)->hyphenStatesArray = holdOffset;
	/* Prevents segmentation fault if table is reallocated */
	memcpy(&(*table)->ruleArea[(*table)->hyphenStatesArray], &dict.states[0],
			dict.numStates * sizeof(HyphenationState));
	free(dict.states);
	return 1;
}

static int
compileCharDef(FileInfo *nested, TranslationTableOpcode opcode,
		TranslationTableCharacterAttributes attributes, int *lastToken,
		TranslationTableOffset *newRuleOffset, TranslationTableRule **newRule, int noback,
		int nofor, TranslationTableHeader **table, DisplayTableHeader **displayTable) {
	CharsString ruleChars;
	CharsString ruleDots;
	if (!getRuleCharsText(nested, &ruleChars, lastToken)) return 0;
	if (!getRuleDotsPattern(nested, &ruleDots, lastToken)) return 0;
	if (ruleChars.length != 1) {
		compileError(nested, "Exactly one character is required.");
		return 0;
	}
	if (ruleDots.length < 1) {
		compileError(nested, "At least one cell is required.");
		return 0;
	}
	if (table) {
		TranslationTableCharacter *character;
		TranslationTableCharacter *cell = NULL;
		int k;
		if (attributes & (CTC_UpperCase | CTC_LowerCase)) attributes |= CTC_Letter;
		character = putChar(nested, ruleChars.chars[0], table);
		character->attributes |= attributes;
		character->uppercase = character->lowercase = character->realchar;
		for (k = ruleDots.length - 1; k >= 0; k -= 1) {
			cell = getDots(ruleDots.chars[k], *table);
			if (!cell) {
				cell = putDots(nested, ruleDots.chars[k], table);
				cell->uppercase = cell->lowercase = cell->realchar;
			}
		}
		if (ruleDots.length == 1) cell->attributes |= attributes;
	}
	if (displayTable && ruleDots.length == 1)
		putCharDotsMapping(nested, ruleChars.chars[0], ruleDots.chars[0], displayTable);
	if (table)
		if (!addRule(nested, opcode, &ruleChars, &ruleDots, 0, 0, newRuleOffset, newRule,
					noback, nofor, table))
			return 0;
	return 1;
}

static int
compileBeforeAfter(FileInfo *nested, int *lastToken) {
	/* 1=before, 2=after, 0=error */
	CharsString token;
	CharsString tmp;
	if (getToken(nested, &token, "last word before or after", lastToken))
		if (parseChars(nested, &tmp, &token)) {
			if (eqasc2uni((unsigned char *)"before", tmp.chars, 6)) return 1;
			if (eqasc2uni((unsigned char *)"after", tmp.chars, 5)) return 2;
		}
	return 0;
}

static int
compileRule(FileInfo *nested, TranslationTableOffset *newRuleOffset,
		TranslationTableRule **newRule, TranslationTableHeader **table,
		DisplayTableHeader **displayTable) {
	int lastToken = 0;
	int ok = 1;
	CharsString token;
	TranslationTableOpcode opcode;
	CharsString ruleChars;
	CharsString ruleDots;
	CharsString cells;
	CharsString scratchPad;
	CharsString emphClass;
	TranslationTableCharacterAttributes after = 0;
	TranslationTableCharacterAttributes before = 0;
	TranslationTableCharacter *c = NULL;
	widechar *patterns = NULL;
	int k, i;
	int noback, nofor, nocross;
	noback = nofor = nocross = 0;
doOpcode:
	if (!getToken(nested, &token, NULL, &lastToken)) return 1;	/* blank line */
	if (token.chars[0] == '#' || token.chars[0] == '<') return 1; /* comment */
	if (nested->lineNumber == 1 &&
			(eqasc2uni((unsigned char *)"ISO", token.chars, 3) ||
					eqasc2uni((unsigned char *)"UTF-8", token.chars, 5))) {
		if (table)
			compileHyphenation(nested, &token, &lastToken, table);
		else
			/* ignore the whole file */
			while (_lou_getALine(nested))
				;
		return 1;
	}
	opcode = getOpcode(nested, &token);
	switch (opcode) {
	case CTO_IncludeFile: {
		CharsString includedFile;
		if (getToken(nested, &token, "include file name", &lastToken))
			if (parseChars(nested, &includedFile, &token))
				if (!includeFile(nested, &includedFile, table, displayTable)) ok = 0;
		break;
	}
	case CTO_NoBack:
		if (nofor) {
			compileError(nested, "%s already specified.", _lou_findOpcodeName(CTO_NoFor));
			ok = 0;
			break;
		}
		noback = 1;
		goto doOpcode;
	case CTO_NoFor:
		if (noback) {
			compileError(
					nested, "%s already specified.", _lou_findOpcodeName(CTO_NoBack));
			ok = 0;
			break;
		}
		nofor = 1;
		goto doOpcode;
	case CTO_Space:
		compileCharDef(nested, opcode, CTC_Space, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_Digit:
		compileCharDef(nested, opcode, CTC_Digit, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_LitDigit:
		compileCharDef(nested, opcode, CTC_LitDigit, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_Punctuation:
		compileCharDef(nested, opcode, CTC_Punctuation, &lastToken, newRuleOffset,
				newRule, noback, nofor, table, displayTable);
		break;
	case CTO_Math:
		compileCharDef(nested, opcode, CTC_Math, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_Sign:
		compileCharDef(nested, opcode, CTC_Sign, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_Letter:
		compileCharDef(nested, opcode, CTC_Letter, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_UpperCase:
		compileCharDef(nested, opcode, CTC_UpperCase, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_LowerCase:
		compileCharDef(nested, opcode, CTC_LowerCase, &lastToken, newRuleOffset, newRule,
				noback, nofor, table, displayTable);
		break;
	case CTO_Grouping:
		ok = compileGrouping(nested, &lastToken, newRuleOffset, newRule, noback, nofor,
				table, displayTable);
		break;
	case CTO_UpLow:
		ok = compileUplow(nested, &lastToken, newRuleOffset, newRule, noback, nofor,
				table, displayTable);
		break;
	case CTO_Display:
		if (!displayTable) break;
		if (getRuleCharsText(nested, &ruleChars, &lastToken))
			if (getRuleDotsPattern(nested, &ruleDots, &lastToken)) {
				if (ruleChars.length != 1 || ruleDots.length != 1) {
					compileError(
							nested, "Exactly one character and one cell are required.");
					ok = 0;
				}
				putCharDotsMapping(
						nested, ruleChars.chars[0], ruleDots.chars[0], displayTable);
			}
		break;
	/* now only opcodes follow that don't modify the display table */
	default:
		if (!table) break;
		switch (opcode) {
		case CTO_None:
			break;
		case CTO_Locale:
			compileWarning(nested,
					"The locale opcode is not implemented. Use the locale meta data "
					"instead.");
			break;
		case CTO_Undefined: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->undefined;
			ok = compileBrailleIndicator(nested, "undefined character opcode",
					CTO_Undefined, &lastToken, &ruleOffset, newRule, noback, nofor,
					table);
			(*table)->undefined = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_Match: {
			TranslationTableRule *rule;
			TranslationTableOffset ruleOffset;
			CharsString ptn_before, ptn_after;
			TranslationTableOffset patternsOffset;
			int len, mrk;

			size_t patternsByteSize = sizeof(*patterns) * 27720;
			patterns = (widechar *)malloc(patternsByteSize);
			if (!patterns) _lou_outOfMemory();
			memset(patterns, 0xffff, patternsByteSize);

			noback = 1;
			getCharacters(nested, &ptn_before, &lastToken);
			getRuleCharsText(nested, &ruleChars, &lastToken);
			getCharacters(nested, &ptn_after, &lastToken);
			getRuleDotsPattern(nested, &ruleDots, &lastToken);

			if (!addRule(nested, opcode, &ruleChars, &ruleDots, after, before,
						&ruleOffset, &rule, noback, nofor, table)) {
				ok = 0;
				break;
			}
			if (ptn_before.chars[0] == '-' && ptn_before.length == 1)
				len = _lou_pattern_compile(
						&ptn_before.chars[0], 0, &patterns[1], 13841, *table, nested);
			else
				len = _lou_pattern_compile(&ptn_before.chars[0], ptn_before.length,
						&patterns[1], 13841, *table, nested);
			if (!len) {
				ok = 0;
				break;
			}
			mrk = patterns[0] = len + 1;
			_lou_pattern_reverse(&patterns[1]);

			if (ptn_after.chars[0] == '-' && ptn_after.length == 1)
				len = _lou_pattern_compile(
						&ptn_after.chars[0], 0, &patterns[mrk], 13841, *table, nested);
			else
				len = _lou_pattern_compile(&ptn_after.chars[0], ptn_after.length,
						&patterns[mrk], 13841, *table, nested);
			if (!len) {
				ok = 0;
				break;
			}
			len += mrk;

			if (!allocateSpaceInTranslationTable(
						nested, &patternsOffset, len * sizeof(widechar), table)) {
				ok = 0;
				break;
			}

			/* realloc may have moved table, so make sure rule is still valid */
			rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];
			memcpy(&(*table)->ruleArea[patternsOffset], patterns, len * sizeof(widechar));
			rule->patterns = patternsOffset;

			if (newRule) *newRule = rule;
			if (newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}

		case CTO_BackMatch: {
			TranslationTableRule *rule;
			TranslationTableOffset ruleOffset;
			CharsString ptn_before, ptn_after;
			TranslationTableOffset patternOffset;
			int len, mrk;

			size_t patternsByteSize = sizeof(*patterns) * 27720;
			patterns = (widechar *)malloc(patternsByteSize);
			if (!patterns) _lou_outOfMemory();
			memset(patterns, 0xffff, patternsByteSize);

			nofor = 1;
			getCharacters(nested, &ptn_before, &lastToken);
			getRuleCharsText(nested, &ruleChars, &lastToken);
			getCharacters(nested, &ptn_after, &lastToken);
			getRuleDotsPattern(nested, &ruleDots, &lastToken);

			if (!addRule(nested, opcode, &ruleChars, &ruleDots, 0, 0, &ruleOffset, &rule,
						noback, nofor, table)) {
				ok = 0;
				break;
			}
			if (ptn_before.chars[0] == '-' && ptn_before.length == 1)
				len = _lou_pattern_compile(
						&ptn_before.chars[0], 0, &patterns[1], 13841, *table, nested);
			else
				len = _lou_pattern_compile(&ptn_before.chars[0], ptn_before.length,
						&patterns[1], 13841, *table, nested);
			if (!len) {
				ok = 0;
				break;
			}
			mrk = patterns[0] = len + 1;
			_lou_pattern_reverse(&patterns[1]);

			if (ptn_after.chars[0] == '-' && ptn_after.length == 1)
				len = _lou_pattern_compile(
						&ptn_after.chars[0], 0, &patterns[mrk], 13841, *table, nested);
			else
				len = _lou_pattern_compile(&ptn_after.chars[0], ptn_after.length,
						&patterns[mrk], 13841, *table, nested);
			if (!len) {
				ok = 0;
				break;
			}
			len += mrk;

			if (!allocateSpaceInTranslationTable(
						nested, &patternOffset, len * sizeof(widechar), table)) {
				ok = 0;
				break;
			}

			/* realloc may have moved table, so make sure rule is still valid */
			rule = (TranslationTableRule *)&(*table)->ruleArea[ruleOffset];

			memcpy(&(*table)->ruleArea[patternOffset], patterns, len * sizeof(widechar));
			rule->patterns = patternOffset;

			if (newRule) *newRule = rule;
			if (newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}

		case CTO_BegCapsPhrase: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset =
					(*table)->emphRules[capsRule][begPhraseOffset];
			ok = compileBrailleIndicator(nested, "first word capital sign",
					CTO_BegCapsPhraseRule, &lastToken, &ruleOffset, newRule, noback,
					nofor, table);
			(*table)->emphRules[capsRule][begPhraseOffset] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_EndCapsPhrase: {
			TranslationTableOffset ruleOffset;
			switch (compileBeforeAfter(nested, &lastToken)) {
			case 1:  // before
				if ((*table)->emphRules[capsRule][endPhraseAfterOffset]) {
					compileError(nested, "Capital sign after last word already defined.");
					ok = 0;
					break;
				}
				// not passing pointer because compileBrailleIndicator may reallocate
				// table
				ruleOffset = (*table)->emphRules[capsRule][endPhraseBeforeOffset];
				ok = compileBrailleIndicator(nested, "capital sign before last word",
						CTO_EndCapsPhraseBeforeRule, &lastToken, &ruleOffset, newRule,
						noback, nofor, table);
				(*table)->emphRules[capsRule][endPhraseBeforeOffset] = ruleOffset;
				if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
				break;
			case 2:  // after
				if ((*table)->emphRules[capsRule][endPhraseBeforeOffset]) {
					compileError(
							nested, "Capital sign before last word already defined.");
					ok = 0;
					break;
				}
				// not passing pointer because compileBrailleIndicator may reallocate
				// table
				ruleOffset = (*table)->emphRules[capsRule][endPhraseAfterOffset];
				ok = compileBrailleIndicator(nested, "capital sign after last word",
						CTO_EndCapsPhraseAfterRule, &lastToken, &ruleOffset, newRule,
						noback, nofor, table);
				(*table)->emphRules[capsRule][endPhraseAfterOffset] = ruleOffset;
				if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
				break;
			default:  // error
				compileError(nested, "Invalid lastword indicator location.");
				ok = 0;
				break;
			}
			break;
		}
		case CTO_BegCaps: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->emphRules[capsRule][begOffset];
			ok = compileBrailleIndicator(nested, "first letter capital sign",
					CTO_BegCapsRule, &lastToken, &ruleOffset, newRule, noback, nofor,
					table);
			(*table)->emphRules[capsRule][begOffset] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_EndCaps: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->emphRules[capsRule][endOffset];
			ok = compileBrailleIndicator(nested, "last letter capital sign",
					CTO_EndCapsRule, &lastToken, &ruleOffset, newRule, noback, nofor,
					table);
			(*table)->emphRules[capsRule][endOffset] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_CapsLetter: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset =
					(*table)->emphRules[capsRule][letterOffset];
			ok = compileBrailleIndicator(nested, "single letter capital sign",
					CTO_CapsLetterRule, &lastToken, &ruleOffset, newRule, noback, nofor,
					table);
			(*table)->emphRules[capsRule][letterOffset] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_BegCapsWord: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset =
					(*table)->emphRules[capsRule][begWordOffset];
			ok = compileBrailleIndicator(nested, "capital word", CTO_BegCapsWordRule,
					&lastToken, &ruleOffset, newRule, noback, nofor, table);
			(*table)->emphRules[capsRule][begWordOffset] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_EndCapsWord: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset =
					(*table)->emphRules[capsRule][endWordOffset];
			ok = compileBrailleIndicator(nested, "capital word stop", CTO_EndCapsWordRule,
					&lastToken, &ruleOffset, newRule, noback, nofor, table);
			(*table)->emphRules[capsRule][endWordOffset] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_LenCapsPhrase:
			ok = (*table)->emphRules[capsRule][lenPhraseOffset] =
					compileNumber(nested, &lastToken);
			break;

		/* these 9 general purpose emphasis opcodes are compiled further down to more
		 * specific internal opcodes:
		 * - emphletter
		 * - begemphword
		 * - endemphword
		 * - begemph
		 * - endemph
		 * - begemphphrase
		 * - endemphphrase
		 * - lenemphphrase
		 */
		case CTO_EmphClass:
			if (getToken(nested, &token, "emphasis class", &lastToken))
				if (parseChars(nested, &emphClass, &token)) {
					char *s = malloc(sizeof(char) * (emphClass.length + 1));
					for (k = 0; k < emphClass.length; k++)
						s[k] = (char)emphClass.chars[k];
					s[k++] = '\0';
					for (i = 0; (*table)->emphClasses[i]; i++)
						if (strcmp(s, (*table)->emphClasses[i]) == 0) {
							_lou_logMessage(
									LOU_LOG_WARN, "Duplicate emphasis class: %s", s);
							warningCount++;
							free(s);
							return 1;
						}
					if (i < MAX_EMPH_CLASSES) {
						switch (i) {
						/* For backwards compatibility (i.e. because programs will assume
						 * the first 3 typeform bits are `italic', `underline' and `bold')
						 * we require that the first 3 emphclass definitions are (in that
						 * order):
						 *
						 *   emphclass italic
						 *   emphclass underline
						 *   emphclass bold
						 *
						 * While it would be possible to use the emphclass opcode only for
						 * defining
						 * _additional_ classes (not allowing for them to be called
						 * italic, underline or bold), thereby reducing the amount of
						 * boilerplate, we deliberately choose not to do that in order to
						 * not give italic, underline and bold any special status. The
						 * hope is that eventually all programs will use liblouis for
						 * emphasis the recommended way (i.e. by looking up the supported
						 * typeforms in
						 * the documentation or API) so that we can drop this restriction.
						 */
						case 0:
							if (strcmp(s, "italic") != 0) {
								_lou_logMessage(LOU_LOG_ERROR,
										"First emphasis class must be \"italic\" but got "
										"%s",
										s);
								errorCount++;
								free(s);
								return 0;
							}
							break;
						case 1:
							if (strcmp(s, "underline") != 0) {
								_lou_logMessage(LOU_LOG_ERROR,
										"Second emphasis class must be \"underline\" but "
										"got "
										"%s",
										s);
								errorCount++;
								free(s);
								return 0;
							}
							break;
						case 2:
							if (strcmp(s, "bold") != 0) {
								_lou_logMessage(LOU_LOG_ERROR,
										"Third emphasis class must be \"bold\" but got "
										"%s",
										s);
								errorCount++;
								free(s);
								return 0;
							}
							break;
						}
						(*table)->emphClasses[i] = s;
						(*table)->emphClasses[i + 1] = NULL;
						ok = 1;
						break;
					} else {
						_lou_logMessage(LOU_LOG_ERROR,
								"Max number of emphasis classes (%i) reached",
								MAX_EMPH_CLASSES);
						errorCount++;
						free(s);
						ok = 0;
						break;
					}
				}
			compileError(nested, "emphclass must be followed by a valid class name.");
			ok = 0;
			break;
		case CTO_EmphLetter:
		case CTO_BegEmphWord:
		case CTO_EndEmphWord:
		case CTO_BegEmph:
		case CTO_EndEmph:
		case CTO_BegEmphPhrase:
		case CTO_EndEmphPhrase:
		case CTO_LenEmphPhrase: {
			ok = 0;
			TranslationTableOffset ruleOffset = 0;
			if (getToken(nested, &token, "emphasis class", &lastToken))
				if (parseChars(nested, &emphClass, &token)) {
					char *s = malloc(sizeof(char) * (emphClass.length + 1));
					for (k = 0; k < emphClass.length; k++)
						s[k] = (char)emphClass.chars[k];
					s[k++] = '\0';
					for (i = 0; (*table)->emphClasses[i]; i++)
						if (strcmp(s, (*table)->emphClasses[i]) == 0) break;
					if (!(*table)->emphClasses[i]) {
						_lou_logMessage(
								LOU_LOG_ERROR, "Emphasis class %s not declared", s);
						errorCount++;
						free(s);
						break;
					}
					i++;  // in table->emphRules the first index is used for caps
					if (opcode == CTO_EmphLetter) {
						// not passing pointer because compileBrailleIndicator may
						// reallocate table
						ruleOffset = (*table)->emphRules[i][letterOffset];
						ok = compileBrailleIndicator(nested, "single letter",
								CTO_Emph1LetterRule + letterOffset + (8 * i), &lastToken,
								&ruleOffset, newRule, noback, nofor, table);
						(*table)->emphRules[i][letterOffset] = ruleOffset;
					} else if (opcode == CTO_BegEmphWord) {
						// not passing pointer because compileBrailleIndicator may
						// reallocate table
						ruleOffset = (*table)->emphRules[i][begWordOffset];
						ok = compileBrailleIndicator(nested, "word",
								CTO_Emph1LetterRule + begWordOffset + (8 * i), &lastToken,
								&ruleOffset, newRule, noback, nofor, table);
						(*table)->emphRules[i][begWordOffset] = ruleOffset;
					} else if (opcode == CTO_EndEmphWord) {
						// not passing pointer because compileBrailleIndicator may
						// reallocate table
						ruleOffset = (*table)->emphRules[i][endWordOffset];
						ok = compileBrailleIndicator(nested, "word stop",
								CTO_Emph1LetterRule + endWordOffset + (8 * i), &lastToken,
								&ruleOffset, newRule, noback, nofor, table);
						(*table)->emphRules[i][endWordOffset] = ruleOffset;
					} else if (opcode == CTO_BegEmph) {
						/* fail if both begemph and any of begemphphrase or begemphword
						 * are defined */
						if ((*table)->emphRules[i][begWordOffset] ||
								(*table)->emphRules[i][begPhraseOffset]) {
							compileError(nested,
									"Cannot define emphasis for both no context and word "
									"or "
									"phrase context, i.e. cannot have both begemph and "
									"begemphword or begemphphrase.");
							ok = 0;
							break;
						}
						// not passing pointer because compileBrailleIndicator may
						// reallocate table
						ruleOffset = (*table)->emphRules[i][begOffset];
						ok = compileBrailleIndicator(nested, "first letter",
								CTO_Emph1LetterRule + begOffset + (8 * i), &lastToken,
								&ruleOffset, newRule, noback, nofor, table);
						(*table)->emphRules[i][begOffset] = ruleOffset;
					} else if (opcode == CTO_EndEmph) {
						if ((*table)->emphRules[i][endWordOffset] ||
								(*table)->emphRules[i][endPhraseBeforeOffset] ||
								(*table)->emphRules[i][endPhraseAfterOffset]) {
							compileError(nested,
									"Cannot define emphasis for both no context and word "
									"or "
									"phrase context, i.e. cannot have both endemph and "
									"endemphword or endemphphrase.");
							ok = 0;
							break;
						}
						// not passing pointer because compileBrailleIndicator may
						// reallocate table
						ruleOffset = (*table)->emphRules[i][endOffset];
						ok = compileBrailleIndicator(nested, "last letter",
								CTO_Emph1LetterRule + endOffset + (8 * i), &lastToken,
								&ruleOffset, newRule, noback, nofor, table);
						(*table)->emphRules[i][endOffset] = ruleOffset;
					} else if (opcode == CTO_BegEmphPhrase) {
						// not passing pointer because compileBrailleIndicator may
						// reallocate table
						ruleOffset = (*table)->emphRules[i][begPhraseOffset];
						ok = compileBrailleIndicator(nested, "first word",
								CTO_Emph1LetterRule + begPhraseOffset + (8 * i),
								&lastToken, &ruleOffset, newRule, noback, nofor, table);
						(*table)->emphRules[i][begPhraseOffset] = ruleOffset;
					} else if (opcode == CTO_EndEmphPhrase)
						switch (compileBeforeAfter(nested, &lastToken)) {
						case 1:  // before
							if ((*table)->emphRules[i][endPhraseAfterOffset]) {
								compileError(nested, "last word after already defined.");
								ok = 0;
								break;
							}
							// not passing pointer because compileBrailleIndicator may
							// reallocate table
							ruleOffset = (*table)->emphRules[i][endPhraseBeforeOffset];
							ok = compileBrailleIndicator(nested, "last word before",
									CTO_Emph1LetterRule + endPhraseBeforeOffset + (8 * i),
									&lastToken, &ruleOffset, newRule, noback, nofor,
									table);
							(*table)->emphRules[i][endPhraseBeforeOffset] = ruleOffset;
							break;
						case 2:  // after
							if ((*table)->emphRules[i][endPhraseBeforeOffset]) {
								compileError(nested, "last word before already defined.");
								ok = 0;
								break;
							}
							// not passing pointer because compileBrailleIndicator may
							// reallocate table
							ruleOffset = (*table)->emphRules[i][endPhraseAfterOffset];
							ok = compileBrailleIndicator(nested, "last word after",
									CTO_Emph1LetterRule + endPhraseAfterOffset + (8 * i),
									&lastToken, &ruleOffset, newRule, noback, nofor,
									table);
							(*table)->emphRules[i][endPhraseAfterOffset] = ruleOffset;
							break;
						default:  // error
							compileError(nested, "Invalid lastword indicator location.");
							ok = 0;
							break;
						}
					else if (opcode == CTO_LenEmphPhrase)
						ok = (*table)->emphRules[i][lenPhraseOffset] =
								compileNumber(nested, &lastToken);
					free(s);
				}
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_LetterSign: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->letterSign;
			ok = compileBrailleIndicator(nested, "letter sign", CTO_LetterRule,
					&lastToken, &ruleOffset, newRule, noback, nofor, table);
			(*table)->letterSign = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_NoLetsignBefore:
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				if (((*table)->noLetsignBeforeCount + ruleChars.length) >
						LETSIGNBEFORESIZE) {
					compileError(nested, "More than %d characters", LETSIGNBEFORESIZE);
					ok = 0;
					break;
				}
				for (k = 0; k < ruleChars.length; k++)
					(*table)->noLetsignBefore[(*table)->noLetsignBeforeCount++] =
							ruleChars.chars[k];
			}
			break;
		case CTO_NoLetsign:
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				if (((*table)->noLetsignCount + ruleChars.length) > LETSIGNSIZE) {
					compileError(nested, "More than %d characters", LETSIGNSIZE);
					ok = 0;
					break;
				}
				for (k = 0; k < ruleChars.length; k++)
					(*table)->noLetsign[(*table)->noLetsignCount++] = ruleChars.chars[k];
			}
			break;
		case CTO_NoLetsignAfter:
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				if (((*table)->noLetsignAfterCount + ruleChars.length) >
						LETSIGNAFTERSIZE) {
					compileError(nested, "More than %d characters", LETSIGNAFTERSIZE);
					ok = 0;
					break;
				}
				for (k = 0; k < ruleChars.length; k++)
					(*table)->noLetsignAfter[(*table)->noLetsignAfterCount++] =
							ruleChars.chars[k];
			}
			break;
		case CTO_NumberSign: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->numberSign;
			ok = compileBrailleIndicator(nested, "number sign", CTO_NumberRule,
					&lastToken, &ruleOffset, newRule, noback, nofor, table);
			(*table)->numberSign = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}

		case CTO_NumericModeChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_NumericMode;
					else {
						compileError(nested, "Numeric mode character undefined");
						ok = 0;
						break;
					}
				}
				(*table)->usesNumericMode = 1;
			}
			break;

		case CTO_MidEndNumericModeChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_MidEndNumericMode;
					else {
						compileError(nested, "Midendnumeric mode character undefined");
						ok = 0;
						break;
					}
				}
				(*table)->usesNumericMode = 1;
			}
			break;

		case CTO_NumericNoContractChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_NumericNoContract;
					else {
						compileError(
								nested, "Numeric no contraction character undefined");
						ok = 0;
						break;
					}
				}
				(*table)->usesNumericMode = 1;
			}
			break;

		case CTO_NoContractSign: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->noContractSign;
			ok = compileBrailleIndicator(nested, "no contractions sign",
					CTO_NoContractRule, &lastToken, &ruleOffset, newRule, noback, nofor,
					table);
			(*table)->noContractSign = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_SeqDelimiter:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_SeqDelimiter;
					else {
						compileError(nested, "Sequence delimiter character undefined");
						ok = 0;
						break;
					}
				}
				(*table)->usesSequences = 1;
			}
			break;

		case CTO_SeqBeforeChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_SeqBefore;
					else {
						compileError(nested, "Sequence before character undefined");
						ok = 0;
						break;
					}
				}
			}
			break;

		case CTO_SeqAfterChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_SeqAfter;
					else {
						compileError(nested, "Sequence after character undefined");
						ok = 0;
						break;
					}
				}
			}
			break;

		case CTO_SeqAfterPattern:

			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				if (((*table)->seqPatternsCount + ruleChars.length + 1) >
						SEQPATTERNSIZE) {
					compileError(nested, "More than %d characters", SEQPATTERNSIZE);
					ok = 0;
					break;
				}
				for (k = 0; k < ruleChars.length; k++)
					(*table)->seqPatterns[(*table)->seqPatternsCount++] =
							ruleChars.chars[k];
				(*table)->seqPatterns[(*table)->seqPatternsCount++] = 0;
			}
			break;
		case CTO_SeqAfterExpression:

			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for ((*table)->seqAfterExpressionLength = 0;
						(*table)->seqAfterExpressionLength < ruleChars.length;
						(*table)->seqAfterExpressionLength++)
					(*table)->seqAfterExpression[(*table)->seqAfterExpressionLength] =
							ruleChars.chars[(*table)->seqAfterExpressionLength];
				(*table)->seqAfterExpression[(*table)->seqAfterExpressionLength] = 0;
			}
			break;

		case CTO_CapsModeChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_CapsMode;
					else {
						compileError(nested, "Capital mode character undefined");
						ok = 0;
						break;
					}
				}
			}
			break;

		case CTO_EmphModeChars:

			c = NULL;
			ok = 1;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (c)
						c->attributes |= CTC_EmphMode;
					else {
						compileError(nested, "Emphasis mode character undefined");
						ok = 0;
						break;
					}
				}
			}
			(*table)->usesEmphMode = 1;
			break;

		case CTO_BegComp: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->begComp;
			ok = compileBrailleIndicator(nested, "begin computer braille",
					CTO_BegCompRule, &lastToken, &ruleOffset, newRule, noback, nofor,
					table);
			(*table)->begComp = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_EndComp: {
			// not passing pointer because compileBrailleIndicator may reallocate table
			TranslationTableOffset ruleOffset = (*table)->endComp;
			ok = compileBrailleIndicator(nested, "end computer braslle", CTO_EndCompRule,
					&lastToken, &ruleOffset, newRule, noback, nofor, table);
			(*table)->endComp = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_NoCross:
			if (nocross) {
				compileError(nested, "%s already specified.",
						_lou_findOpcodeName(CTO_NoCross));
				ok = 0;
				break;
			}
			nocross = 1;
			goto doOpcode;
		case CTO_Syllable:
			(*table)->syllables = 1;
		case CTO_Always:
		case CTO_LargeSign:
		case CTO_WholeWord:
		case CTO_PartWord:
		case CTO_JoinNum:
		case CTO_JoinableWord:
		case CTO_LowWord:
		case CTO_SuffixableWord:
		case CTO_PrefixableWord:
		case CTO_BegWord:
		case CTO_BegMidWord:
		case CTO_MidWord:
		case CTO_MidEndWord:
		case CTO_EndWord:
		case CTO_PrePunc:
		case CTO_PostPunc:
		case CTO_BegNum:
		case CTO_MidNum:
		case CTO_EndNum:
		case CTO_Repeated:
		case CTO_RepWord:
			ok = 0;
			if (getRuleCharsText(nested, &ruleChars, &lastToken))
				if (getRuleDotsPattern(nested, &ruleDots, &lastToken)) {
					if (ruleDots.length == 0)  // `=`
						for (k = 0; k < ruleChars.length; k++) {
							c = getChar(ruleChars.chars[k], *table);
							if (!c || !c->definitionRule) {
								compileError(nested, "Character %s is not defined",
										_lou_showString(&ruleChars.chars[k], 1, 0));
								return 0;
							}
						}
					TranslationTableRule *r;
					if (addRule(nested, opcode, &ruleChars, &ruleDots, after, before,
								newRuleOffset, &r, noback, nofor, table)) {
						if (nocross) r->nocross = 1;
						if (newRule) *newRule = r;
						ok = 1;
					}
				}
			// if (opcode == CTO_MidNum)
			// {
			//   TranslationTableCharacter *c = getChar(ruleChars.chars[0]);
			//   if(c)
			//     c->attributes |= CTC_NumericMode;
			// }
			break;
		case CTO_RepEndWord:
			ok = 0;
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				CharsString dots;
				if (getToken(nested, &dots, "dots,dots operand", &lastToken)) {
					int len = dots.length;
					for (k = 0; k < len - 1; k++) {
						if (dots.chars[k] == ',') {
							dots.length = k;
							if (parseDots(nested, &ruleDots, &dots)) {
								ruleDots.chars[ruleDots.length++] = ',';
								k++;
								if (k == len - 1 && dots.chars[k] == '=') {
									for (int l = 0; l < ruleChars.length; l++) {
										c = getChar(ruleChars.chars[l], *table);
										if (!c || !c->definitionRule) {
											compileError(nested,
													"Character %s is not defined",
													_lou_showString(
															&ruleChars.chars[l], 1, 0));
											return 0;
										}
									}
								} else {
									CharsString x, y;
									x.length = 0;
									while (k < len) x.chars[x.length++] = dots.chars[k++];
									if (parseDots(nested, &y, &x))
										for (int l = 0; l < y.length; l++)
											ruleDots.chars[ruleDots.length++] =
													y.chars[l];
								}
								if (addRule(nested, opcode, &ruleChars, &ruleDots, after,
											before, newRuleOffset, newRule, noback, nofor,
											table))
									ok = 1;
								break;
							}
						}
					}
				}
			}
			break;
		case CTO_CompDots:
		case CTO_Comp6: {
			TranslationTableOffset ruleOffset;
			if (!getRuleCharsText(nested, &ruleChars, &lastToken)) return 0;
			if (ruleChars.length != 1 || ruleChars.chars[0] > 255) {
				compileError(nested, "first operand must be 1 character and < 256");
				return 0;
			}
			if (!getRuleDotsPattern(nested, &ruleDots, &lastToken)) return 0;
			if (!addRule(nested, opcode, &ruleChars, &ruleDots, after, before,
						&ruleOffset, newRule, noback, nofor, table))
				ok = 0;
			(*table)->compdotsPattern[ruleChars.chars[0]] = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_ExactDots:
			if (!getRuleCharsText(nested, &ruleChars, &lastToken)) return 0;
			if (ruleChars.chars[0] != '@') {
				compileError(nested, "The operand must begin with an at sign (@)");
				return 0;
			}
			for (k = 1; k < ruleChars.length; k++)
				scratchPad.chars[k - 1] = ruleChars.chars[k];
			scratchPad.length = ruleChars.length - 1;
			if (!parseDots(nested, &ruleDots, &scratchPad)) return 0;
			if (!addRule(nested, opcode, &ruleChars, &ruleDots, before, after,
						newRuleOffset, newRule, noback, nofor, table))
				ok = 0;
			break;
		case CTO_CapsNoCont: {
			TranslationTableOffset ruleOffset;
			ruleChars.length = 1;
			ruleChars.chars[0] = 'a';
			if (!addRule(nested, CTO_CapsNoContRule, &ruleChars, NULL, after, before,
						&ruleOffset, newRule, noback, nofor, table))
				ok = 0;
			(*table)->capsNoCont = ruleOffset;
			if (ok && newRuleOffset) *newRuleOffset = ruleOffset;
			break;
		}
		case CTO_Replace:
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				if (lastToken)
					ruleDots.length = ruleDots.chars[0] = 0;
				else {
					getRuleDotsText(nested, &ruleDots, &lastToken);
					if (ruleDots.chars[0] == '#')
						ruleDots.length = ruleDots.chars[0] = 0;
					else if (ruleDots.chars[0] == '\\' && ruleDots.chars[1] == '#')
						memmove(&ruleDots.chars[0], &ruleDots.chars[1],
								ruleDots.length-- * CHARSIZE);
				}
			}
			for (k = 0; k < ruleChars.length; k++)
				putChar(nested, ruleChars.chars[k], table);
			for (k = 0; k < ruleDots.length; k++)
				putChar(nested, ruleDots.chars[k], table);
			if (!addRule(nested, opcode, &ruleChars, &ruleDots, after, before,
						newRuleOffset, newRule, noback, nofor, table))
				ok = 0;
			break;
		case CTO_Correct:
			(*table)->corrections = 1;
			goto doPass;
		case CTO_Pass2:
			if ((*table)->numPasses < 2) (*table)->numPasses = 2;
			goto doPass;
		case CTO_Pass3:
			if ((*table)->numPasses < 3) (*table)->numPasses = 3;
			goto doPass;
		case CTO_Pass4:
			if ((*table)->numPasses < 4) (*table)->numPasses = 4;
		doPass:
		case CTO_Context:
			if (!(nofor || noback)) {
				compileError(nested, "%s or %s must be specified.",
						_lou_findOpcodeName(CTO_NoFor), _lou_findOpcodeName(CTO_NoBack));
				ok = 0;
				break;
			}
			if (!compilePassOpcode(
						nested, opcode, newRuleOffset, newRule, noback, nofor, table))
				ok = 0;
			break;
		case CTO_Contraction:
		case CTO_NoCont:
		case CTO_CompBrl:
		case CTO_Literal:
			if (getRuleCharsText(nested, &ruleChars, &lastToken)) {
				for (k = 0; k < ruleChars.length; k++) {
					c = getChar(ruleChars.chars[k], *table);
					if (!c || !c->definitionRule) {
						compileError(nested, "Character %s is not defined",
								_lou_showString(&ruleChars.chars[k], 1, 0));
						return 0;
					}
				}
				if (!addRule(nested, opcode, &ruleChars, NULL, after, before,
							newRuleOffset, newRule, noback, nofor, table))
					ok = 0;
			}
			break;
		case CTO_MultInd: {
			int t;
			ruleChars.length = 0;
			if (getToken(nested, &token, "multiple braille indicators", &lastToken) &&
					parseDots(nested, &cells, &token)) {
				while ((t = getToken(nested, &token, "multind opcodes", &lastToken))) {
					opcode = getOpcode(nested, &token);
					if (opcode >= CTO_CapsLetter && opcode < CTO_MultInd)
						ruleChars.chars[ruleChars.length++] = (widechar)opcode;
					else {
						compileError(nested, "Not a braille indicator opcode.");
						ok = 0;
					}
					if (t == 2) break;
				}
			} else
				ok = 0;
			if (!addRule(nested, CTO_MultInd, &ruleChars, &cells, after, before,
						newRuleOffset, newRule, noback, nofor, table))
				ok = 0;
			break;
		}

		case CTO_Class:
			compileWarning(nested, "class is deprecated, use attribute instead");
		case CTO_Attribute: {
			if ((opcode == CTO_Class && (*table)->usesAttributeOrClass == 1) ||
					(opcode == CTO_Attribute && (*table)->usesAttributeOrClass == 2)) {
				compileError(nested,
						"attribute and class rules must not be both present in a table");
				ok = 0;
				break;
			}
			if (opcode == CTO_Class)
				(*table)->usesAttributeOrClass = 2;
			else
				(*table)->usesAttributeOrClass = 1;

			ok = 1;
			if (!getToken(nested, &token, "attribute name", &lastToken)) {
				compileError(nested, "Expected %s", "attribute name");
				ok = 0;
				break;
			}

			if (!(*table)->characterClasses && !allocateCharacterClasses(*table)) {
				ok = 0;
				break;
			}

			TranslationTableCharacterAttributes attribute = 0;
			{
				int attrNumber = -1;
				switch (token.chars[0]) {
				case '0':
				case '1':
				case '2':
				case '3':
				case '4':
				case '5':
				case '6':
				case '7':
				case '8':
				case '9':
					attrNumber = token.chars[0] - '0';
					break;
				}
				if (attrNumber >= 0) {
					if (opcode == CTO_Class) {
						compileError(nested,
								"Invalid class name: may not contain digits, use "
								"attribute instead of class");
						ok = 0;
						break;
					} else if (token.length > 1 || attrNumber > 7) {
						compileError(nested,
								"Invalid attribute name: must be a digit between 0 and 7 "
								"or a word containing only letters");
						ok = 0;
						break;
					}
					if (!(*table)->numberedAttributes[attrNumber])
						// attribute not used before yet: assign it a value
						(*table)->numberedAttributes[attrNumber] =
								getNextNumberedAttribute(*table);
					attribute = (*table)->numberedAttributes[attrNumber];
				} else {
					const CharacterClass *namedAttr = findCharacterClass(&token, *table);
					if (!namedAttr) {
						// no class with that name: create one
						for (i = 0; i < token.length; i++) {
							if (!((token.chars[i] >= 'a' && token.chars[i] <= 'z') ||
										(token.chars[i] >= 'A' &&
												token.chars[i] <= 'Z'))) {
								// don't abort because in some cases (before/after rules)
								// this will work fine, but it will not work in multipass
								// expressions
								compileWarning(nested,
										"Invalid attribute name: must be a digit between "
										"0 and 7 or a word containing only letters");
							}
						}
						// check that name is not reserved
						k = 0;
						while (reservedAttributeNames[k]) {
							if (strlen(reservedAttributeNames[k]) == token.length) {
								for (i = 0; i < token.length; i++)
									if (reservedAttributeNames[k][i] != token.chars[i])
										break;
								if (i == token.length) {
									compileError(nested, "Attribute name is reserved: %s",
											reservedAttributeNames[k]);
									ok = 0;
									break;
								}
							}
							k++;
						}
						if (!ok) break;
						// create the class
						namedAttr = addCharacterClass(
								nested, &token.chars[0], token.length, *table);
					}
					if (namedAttr)
						// there is a class with that name or a new class was successfully
						// created
						attribute = namedAttr->attribute;
				}
			}
			if (!attribute) {
				compileError(nested, "Too many character attributes defined");
				ok = 0;
				break;
			}
			CharsString characters;
			if (getCharacters(nested, &characters, &lastToken)) {
				for (i = 0; i < characters.length; i++) {
					// get the character from the table, or if it is not defined yet,
					// define it
					TranslationTableCharacter *character =
							putChar(nested, characters.chars[i], table);
					// set the attribute
					character->attributes |= attribute;
					// also set the attribute on the associated dots (if any)
					if (character->definitionRule) {
						TranslationTableRule *defRule =
								(TranslationTableRule *)&(*table)
										->ruleArea[character->definitionRule];
						if (defRule->dotslen == 1) {
							character = getDots(
									defRule->charsdots[defRule->charslen], *table);
							if (character) character->attributes |= attribute;
						}
					}
				}
			}
			break;
		}

			{
				TranslationTableCharacterAttributes *attributes;
				const CharacterClass *class;
			case CTO_After:
				attributes = &after;
				goto doBeforeAfter;
			case CTO_Before:
				attributes = &before;
			doBeforeAfter:
				if (!(*table)->characterClasses) {
					if (!allocateCharacterClasses(*table)) ok = 0;
				}
				if (getToken(nested, &token, "attribute name", &lastToken)) {
					if ((class = findCharacterClass(&token, *table))) {
						*attributes |= class->attribute;
						goto doOpcode;
					}
					compileError(nested, "attribute not defined");
				}
				break;
			}

		case CTO_EmpMatchBefore:
			before |= CTC_EmpMatch;
			goto doOpcode;
		case CTO_EmpMatchAfter:
			after |= CTC_EmpMatch;
			goto doOpcode;

		case CTO_SwapCc:
		case CTO_SwapCd:
		case CTO_SwapDd:
			if (!compileSwap(nested, opcode, &lastToken, newRuleOffset, newRule, noback,
						nofor, table))
				ok = 0;
			break;
		case CTO_Hyphen:
		case CTO_DecPoint:
			//	case CTO_Apostrophe:
			//	case CTO_Initial:
			if (getRuleCharsText(nested, &ruleChars, &lastToken))
				if (getRuleDotsPattern(nested, &ruleDots, &lastToken)) {
					if (ruleChars.length != 1 || ruleDots.length < 1) {
						compileError(nested,
								"One Unicode character and at least one cell are "
								"required.");
						ok = 0;
					}
					if (!addRule(nested, opcode, &ruleChars, &ruleDots, after, before,
								newRuleOffset, newRule, noback, nofor, table))
						ok = 0;
					// if (opcode == CTO_DecPoint)
					// {
					//   TranslationTableCharacter *c =
					//   getChar(ruleChars.chars[0]);
					//   if(c)
					//     c->attributes |= CTC_NumericMode;
					// }
				}
			break;
		default:
			compileError(nested, "unimplemented opcode.");
			ok = 0;
			break;
		}
	}

	if (patterns != NULL) free(patterns);

	return ok;
}

int EXPORT_CALL
lou_readCharFromFile(const char *fileName, int *mode) {
	/* Read a character from a file, whether big-endian, little-endian or
	 * ASCII8 */
	int ch;
	static FileInfo nested;
	if (fileName == NULL) return 0;
	if (*mode == 1) {
		*mode = 0;
		nested.fileName = fileName;
		nested.encoding = noEncoding;
		nested.status = 0;
		nested.lineNumber = 0;
		if (!(nested.in = fopen(nested.fileName, "r"))) {
			_lou_logMessage(LOU_LOG_ERROR, "Cannot open file '%s'", nested.fileName);
			*mode = 1;
			return EOF;
		}
	}
	if (nested.in == NULL) {
		*mode = 1;
		return EOF;
	}
	ch = getAChar(&nested);
	if (ch == EOF) {
		fclose(nested.in);
		nested.in = NULL;
		*mode = 1;
	}
	return ch;
}

static int
compileString(const char *inString, TranslationTableHeader **table,
		DisplayTableHeader **displayTable) {
	/* This function can be used to make changes to tables on the fly. */
	int k;
	FileInfo nested;
	if (inString == NULL) return 0;
	memset(&nested, 0, sizeof(nested));
	nested.fileName = inString;
	nested.encoding = noEncoding;
	nested.lineNumber = 1;
	nested.status = 0;
	nested.linepos = 0;
	for (k = 0; inString[k]; k++) nested.line[k] = inString[k];
	nested.line[k] = 0;
	nested.linelen = k;
	return compileRule(&nested, NULL, NULL, table, displayTable);
}

static int
setDefaults(TranslationTableHeader *table) {
	if (!table->emphRules[emph1Rule][lenPhraseOffset])
		table->emphRules[emph1Rule][lenPhraseOffset] = 4;
	if (!table->emphRules[emph2Rule][lenPhraseOffset])
		table->emphRules[emph2Rule][lenPhraseOffset] = 4;
	if (!table->emphRules[emph3Rule][lenPhraseOffset])
		table->emphRules[emph3Rule][lenPhraseOffset] = 4;
	if (table->numPasses == 0) table->numPasses = 1;
	return 1;
}

/* =============== *
 * TABLE RESOLVING *
 * =============== *
 *
 * A table resolver is a function that resolves a `tableList` path against a
 * `base` path, and returns the resolved table(s) as a list of absolute file
 * paths.
 *
 * The function must have the following signature:
 *
 *     char ** (const char * tableList, const char * base)
 *
 * In general, `tableList` is a path in the broad sense. The default
 * implementation accepts only *file* paths. But another implementation could
 * for instance handle URI's. `base` is always a file path however.
 *
 * The idea is to give other programs that use liblouis the ability to define
 * their own table resolver (in C, Java, Python, etc.) when the default
 * resolver is not satisfying. (see also lou_registerTableResolver)
 *
 */

/**
 * Resolve a single (sub)table.
 *
 * Tries to resolve `table` against `base` if base is an absolute path. If
 * that fails, searches `searchPath`.
 *
 */
static char *
resolveSubtable(const char *table, const char *base, const char *searchPath) {
	char *tableFile;
	static struct stat info;

	if (table == NULL || table[0] == '\0') return NULL;
	tableFile = (char *)malloc(MAXSTRING * sizeof(char) * 2);

	//
	// First try to resolve against base
	//
	if (base) {
		int k;
		strcpy(tableFile, base);
		k = (int)strlen(tableFile);
		while (k >= 0 && tableFile[k] != '/' && tableFile[k] != '\\') k--;
		tableFile[++k] = '\0';
		strcat(tableFile, table);
		if (stat(tableFile, &info) == 0 && !(info.st_mode & S_IFDIR)) {
			_lou_logMessage(LOU_LOG_DEBUG, "found table %s", tableFile);
			return tableFile;
		}
	}

	//
	// It could be an absolute path, or a path relative to the current working
	// directory
	//
	strcpy(tableFile, table);
	if (stat(tableFile, &info) == 0 && !(info.st_mode & S_IFDIR)) {
		_lou_logMessage(LOU_LOG_DEBUG, "found table %s", tableFile);
		return tableFile;
	}

	//
	// Then search `LOUIS_TABLEPATH`, `dataPath` and `programPath`
	//
	if (searchPath[0] != '\0') {
		char *dir;
		int last;
		char *cp;
		char *searchPath_copy = strdup(searchPath);
		for (dir = searchPath_copy;; dir = cp + 1) {
			for (cp = dir; *cp != '\0' && *cp != ','; cp++)
				;
			last = (*cp == '\0');
			*cp = '\0';
			if (dir == cp) dir = ".";
			sprintf(tableFile, "%s%c%s", dir, DIR_SEP, table);
			if (stat(tableFile, &info) == 0 && !(info.st_mode & S_IFDIR)) {
				_lou_logMessage(LOU_LOG_DEBUG, "found table %s", tableFile);
				free(searchPath_copy);
				return tableFile;
			}
			if (last) break;
			sprintf(tableFile, "%s%c%s%c%s%c%s", dir, DIR_SEP, "liblouis", DIR_SEP,
					"tables", DIR_SEP, table);
			if (stat(tableFile, &info) == 0 && !(info.st_mode & S_IFDIR)) {
				_lou_logMessage(LOU_LOG_DEBUG, "found table %s", tableFile);
				free(searchPath_copy);
				return tableFile;
			}
			if (last) break;
		}
		free(searchPath_copy);
	}
	free(tableFile);
	return NULL;
}

char *EXPORT_CALL
_lou_getTablePath(void) {
	char searchPath[MAXSTRING];
	char *path;
	char *cp;
	int envset = 0;
	cp = searchPath;
	path = getenv("LOUIS_TABLEPATH");
	if (path != NULL && path[0] != '\0') {
		envset = 1;
		cp += sprintf(cp, ",%s", path);
	}
	path = lou_getDataPath();
	if (path != NULL && path[0] != '\0')
		cp += sprintf(cp, ",%s%c%s%c%s", path, DIR_SEP, "liblouis", DIR_SEP, "tables");
	if (!envset) {
#ifdef _WIN32
		path = lou_getProgramPath();
		if (path != NULL) {
			if (path[0] != '\0')
				cp += sprintf(cp, ",%s%s", path, "\\share\\liblouis\\tables");
			free(path);
		}
#else
		cp += sprintf(cp, ",%s", TABLESDIR);
#endif
	}
	if (searchPath[0] != '\0')
		return strdup(&searchPath[1]);
	else
		return strdup(".");
}

/**
 * The default table resolver
 *
 * Tries to resolve tableList against base. The search path is set to
 * `LOUIS_TABLEPATH`, `dataPath` and `programPath` (in that order).
 *
 * @param table A file path, may be absolute or relative. May be a list of
 *              tables separated by comma's. In that case, the first table
 *              is used as the base for the other subtables.
 * @param base A file path or directory path, or NULL.
 * @return The file paths of the resolved subtables, or NULL if the table
 *         could not be resolved.
 *
 */
char **EXPORT_CALL
_lou_defaultTableResolver(const char *tableList, const char *base) {
	char *searchPath;
	char **tableFiles;
	char *subTable;
	char *tableList_copy;
	char *cp;
	int last;
	int k;

	/* Set up search path */
	searchPath = _lou_getTablePath();

	/* Count number of subtables in table list */
	k = 0;
	for (cp = (char *)tableList; *cp != '\0'; cp++)
		if (*cp == ',') k++;
	tableFiles = (char **)calloc(k + 2, sizeof(char *));
	if (!tableFiles) _lou_outOfMemory();

	/* Resolve subtables */
	k = 0;
	tableList_copy = strdup(tableList);
	for (subTable = tableList_copy;; subTable = cp + 1) {
		for (cp = subTable; *cp != '\0' && *cp != ','; cp++)
			;
		last = (*cp == '\0');
		*cp = '\0';
		if (!(tableFiles[k++] = resolveSubtable(subTable, base, searchPath))) {
			char *path;
			_lou_logMessage(LOU_LOG_ERROR, "Cannot resolve table '%s'", subTable);
			path = getenv("LOUIS_TABLEPATH");
			if (path != NULL && path[0] != '\0')
				_lou_logMessage(LOU_LOG_ERROR, "LOUIS_TABLEPATH=%s", path);
			free(searchPath);
			free(tableList_copy);
			free_tablefiles(tableFiles);
			return NULL;
		}
		if (k == 1) base = subTable;
		if (last) break;
	}
	free(searchPath);
	free(tableList_copy);
	tableFiles[k] = NULL;
	return tableFiles;
}

static char **(EXPORT_CALL *tableResolver)(
		const char *tableList, const char *base) = &_lou_defaultTableResolver;

static char **
copyStringArray(char **array) {
	int len;
	char **copy;
	if (!array) return NULL;
	len = 0;
	while (array[len]) len++;
	copy = malloc((len + 1) * sizeof(char *));
	copy[len] = NULL;
	while (len) {
		len--;
		copy[len] = strdup(array[len]);
	}
	return copy;
}

char **EXPORT_CALL
_lou_resolveTable(const char *tableList, const char *base) {
	char **tableFiles = (*tableResolver)(tableList, base);
	char **result = copyStringArray(tableFiles);
	if (tableResolver == &_lou_defaultTableResolver) free_tablefiles(tableFiles);
	return result;
}

/**
 * Register a new table resolver. Overrides the default resolver.
 *
 * @param resolver The new resolver as a function pointer.
 *
 */
void EXPORT_CALL
lou_registerTableResolver(
		char **(EXPORT_CALL *resolver)(const char *tableList, const char *base)) {
	tableResolver = resolver;
}

static int fileCount = 0;

/**
 * Compile a single file
 *
 */
static int
compileFile(const char *fileName, TranslationTableHeader **table,
		DisplayTableHeader **displayTable) {
	FileInfo nested;
	fileCount++;
	nested.fileName = fileName;
	nested.encoding = noEncoding;
	nested.status = 0;
	nested.lineNumber = 0;
	if ((nested.in = fopen(nested.fileName, "rb"))) {
		while (_lou_getALine(&nested))
			compileRule(&nested, NULL, NULL, table, displayTable);
		fclose(nested.in);
		return 1;
	} else
		_lou_logMessage(LOU_LOG_ERROR, "Cannot open table '%s'", nested.fileName);
	errorCount++;
	return 0;
}

/**
 * Free a char** array
 */
static void
free_tablefiles(char **tables) {
	char **table;
	if (!tables) return;
	for (table = tables; *table; table++) free(*table);
	free(tables);
}

/**
 * Implement include opcode
 *
 */
static int
includeFile(FileInfo *nested, CharsString *includedFile, TranslationTableHeader **table,
		DisplayTableHeader **displayTable) {
	int k;
	char includeThis[MAXSTRING];
	char **tableFiles;
	int rv;
	for (k = 0; k < includedFile->length; k++)
		includeThis[k] = (char)includedFile->chars[k];
	if (k >= MAXSTRING) {
		compileError(nested, "Include statement too long: 'include %s'", includeThis);
		return 0;
	}
	includeThis[k] = 0;
	tableFiles = _lou_resolveTable(includeThis, nested->fileName);
	if (tableFiles == NULL) {
		errorCount++;
		return 0;
	}
	if (tableFiles[1] != NULL) {
		free_tablefiles(tableFiles);
		compileError(nested,
				"Table list not supported in include statement: 'include %s'",
				includeThis);
		return 0;
	}
	rv = compileFile(*tableFiles, table, displayTable);
	free_tablefiles(tableFiles);
	return rv;
}

/**
 * Compile source tables into a table in memory
 *
 */
static int
compileTable(const char *tableList, const char *displayTableList,
		TranslationTableHeader **translationTable, DisplayTableHeader **displayTable) {
	char **tableFiles;
	char **subTable;
	if (translationTable && !tableList) return 0;
	if (displayTable && !displayTableList) return 0;
	if (!translationTable && !displayTable) return 0;
	if (translationTable) *translationTable = NULL;
	if (displayTable) *displayTable = NULL;
	errorCount = warningCount = fileCount = 0;
	if (!opcodeLengths[0]) {
		TranslationTableOpcode opcode;
		for (opcode = 0; opcode < CTO_None; opcode++)
			opcodeLengths[opcode] = (short)strlen(opcodeNames[opcode]);
	}
	if (translationTable) allocateTranslationTable(NULL, translationTable);
	if (displayTable) allocateDisplayTable(NULL, displayTable);

	if (translationTable) {
		(*translationTable)->emphClasses[0] = NULL;
		(*translationTable)->characterClasses = NULL;
		(*translationTable)->ruleNames = NULL;
	}

	/* Compile things that are necesary for the proper operation of
	 * liblouis or liblouisxml or liblouisutdml */
	/* TODO: These definitions seem to be necessary for proper functioning of
	   liblouisutdml. Find a way to satisfy those requirements without hard coding
	   some characters in every table notably behind the users back */
	compileString("space \\xffff 123456789abcdef LOU_ENDSEGMENT", translationTable,
			displayTable);

	if (displayTable && translationTable && strcmp(tableList, displayTableList) == 0) {
		/* Compile the display and translation tables in one go */

		/* Compile all subtables in the list */
		if (!(tableFiles = _lou_resolveTable(tableList, NULL))) {
			errorCount++;
			goto cleanup;
		}
		for (subTable = tableFiles; *subTable; subTable++)
			if (!compileFile(*subTable, translationTable, displayTable)) goto cleanup;
	} else {
		/* Compile the display and translation tables separately */

		if (displayTable) {
			if (!(tableFiles = _lou_resolveTable(displayTableList, NULL))) {
				errorCount++;
				goto cleanup;
			}
			for (subTable = tableFiles; *subTable; subTable++)
				if (!compileFile(*subTable, NULL, displayTable)) goto cleanup;
			free_tablefiles(tableFiles);
			tableFiles = NULL;
		}
		if (translationTable) {
			if (!(tableFiles = _lou_resolveTable(tableList, NULL))) {
				errorCount++;
				goto cleanup;
			}
			for (subTable = tableFiles; *subTable; subTable++)
				if (!compileFile(*subTable, translationTable, NULL)) goto cleanup;
		}
	}

/* Clean up after compiling files */
cleanup:
	free_tablefiles(tableFiles);
	if (warningCount) _lou_logMessage(LOU_LOG_WARN, "%d warnings issued", warningCount);
	if (!errorCount) {
		if (translationTable) setDefaults(*translationTable);
		return 1;
	} else {
		_lou_logMessage(LOU_LOG_ERROR, "%d errors found.", errorCount);
		if (translationTable) {
			if (*translationTable) free(*translationTable);
			*translationTable = NULL;
		}
		if (displayTable) {
			if (*displayTable) free(*displayTable);
			*displayTable = NULL;
		}
		return 0;
	}
}

/* Return the emphasis classes declared in tableList. */
char const **EXPORT_CALL
lou_getEmphClasses(const char *tableList) {
	const char *names[MAX_EMPH_CLASSES + 1];
	unsigned int count = 0;
	const TranslationTableHeader *table = _lou_getTranslationTable(tableList);
	if (!table) return NULL;

	while (count < MAX_EMPH_CLASSES) {
		char const *name = table->emphClasses[count];
		if (!name) break;
		names[count++] = name;
	}
	names[count++] = NULL;

	{
		unsigned int size = count * sizeof(names[0]);
		char const **result = malloc(size);
		if (!result) return NULL;
		/* The void* cast is necessary to stop MSVC from warning about
		 * different 'const' qualifiers (C4090). */
		memcpy((void *)result, names, size);
		return result;
	}
}

void
getTable(const char *tableList, const char *displayTableList,
		TranslationTableHeader **translationTable, DisplayTableHeader **displayTable);

void EXPORT_CALL
_lou_getTable(const char *tableList, const char *displayTableList,
		const TranslationTableHeader **translationTable,
		const DisplayTableHeader **displayTable) {
	TranslationTableHeader *newTable;
	DisplayTableHeader *newDisplayTable;
	getTable(tableList, displayTableList, &newTable, &newDisplayTable);
	*translationTable = newTable;
	*displayTable = newDisplayTable;
}

/* Checks and loads tableList. */
const void *EXPORT_CALL
lou_getTable(const char *tableList) {
	const TranslationTableHeader *table;
	const DisplayTableHeader *displayTable;
	_lou_getTable(tableList, tableList, &table, &displayTable);
	if (!table || !displayTable) return NULL;
	return table;
}

const TranslationTableHeader *EXPORT_CALL
_lou_getTranslationTable(const char *tableList) {
	TranslationTableHeader *table;
	getTable(tableList, NULL, &table, NULL);
	return table;
}

const DisplayTableHeader *EXPORT_CALL
_lou_getDisplayTable(const char *tableList) {
	DisplayTableHeader *table;
	getTable(NULL, tableList, NULL, &table);
	return table;
}

void
getTable(const char *translationTableList, const char *displayTableList,
		TranslationTableHeader **translationTable, DisplayTableHeader **displayTable) {
	/* Keep track of which tables have already been compiled */
	int translationTableListLen, displayTableListLen = 0;
	if (translationTableList == NULL || *translationTableList == 0)
		translationTable = NULL;
	if (displayTableList == NULL || *displayTableList == 0) displayTable = NULL;
	/* See if translation table has already been compiled */
	if (translationTable) {
		translationTableListLen = (int)strlen(translationTableList);
		*translationTable = NULL;
		TranslationTableChainEntry *currentEntry = translationTableChain;
		TranslationTableChainEntry *prevEntry = NULL;
		while (currentEntry != NULL) {
			if (translationTableListLen == currentEntry->tableListLength &&
					(memcmp(&currentEntry->tableList[0], translationTableList,
							translationTableListLen)) == 0) {
				/* Move the table to the top of the table chain. */
				if (prevEntry != NULL) {
					prevEntry->next = currentEntry->next;
					currentEntry->next = translationTableChain;
					translationTableChain = currentEntry;
				}
				*translationTable = currentEntry->table;
				break;
			}
			prevEntry = currentEntry;
			currentEntry = currentEntry->next;
		}
	}
	/* See if display table has already been compiled */
	if (displayTable) {
		displayTableListLen = (int)strlen(displayTableList);
		*displayTable = NULL;
		DisplayTableChainEntry *currentEntry = displayTableChain;
		DisplayTableChainEntry *prevEntry = NULL;
		while (currentEntry != NULL) {
			if (displayTableListLen == currentEntry->tableListLength &&
					(memcmp(&currentEntry->tableList[0], displayTableList,
							displayTableListLen)) == 0) {
				/* Move the table to the top of the table chain. */
				if (prevEntry != NULL) {
					prevEntry->next = currentEntry->next;
					currentEntry->next = displayTableChain;
					displayTableChain = currentEntry;
				}
				*displayTable = currentEntry->table;
				break;
			}
			prevEntry = currentEntry;
			currentEntry = currentEntry->next;
		}
	}
	if ((translationTable && *translationTable == NULL) ||
			(displayTable && *displayTable == NULL)) {
		TranslationTableHeader *newTranslationTable = NULL;
		DisplayTableHeader *newDisplayTable = NULL;
		if (compileTable(translationTableList, displayTableList,
					(translationTable && *translationTable == NULL) ? &newTranslationTable
																	: NULL,
					(displayTable && *displayTable == NULL) ? &newDisplayTable : NULL)) {
			/* Add a new entry to the top of the table chain. */
			if (newTranslationTable != NULL) {
				int entrySize =
						sizeof(TranslationTableChainEntry) + translationTableListLen;
				TranslationTableChainEntry *newEntry = malloc(entrySize);
				if (!newEntry) _lou_outOfMemory();
				newEntry->next = translationTableChain;
				newEntry->table = newTranslationTable;
				newEntry->tableListLength = translationTableListLen;
				memcpy(&newEntry->tableList[0], translationTableList,
						translationTableListLen);
				translationTableChain = newEntry;
				*translationTable = newTranslationTable;
			}
			if (newDisplayTable != NULL) {
				int entrySize = sizeof(DisplayTableChainEntry) + displayTableListLen;
				DisplayTableChainEntry *newEntry = malloc(entrySize);
				if (!newEntry) _lou_outOfMemory();
				newEntry->next = displayTableChain;
				newEntry->table = newDisplayTable;
				newEntry->tableListLength = displayTableListLen;
				memcpy(&newEntry->tableList[0], displayTableList, displayTableListLen);
				displayTableChain = newEntry;
				*displayTable = newDisplayTable;
			}
		} else {
			_lou_logMessage(
					LOU_LOG_ERROR, "%s could not be compiled", translationTableList);
			return;
		}
	}
}

int EXPORT_CALL
lou_checkTable(const char *tableList) {
	if (lou_getTable(tableList)) return 1;
	return 0;
}

formtype EXPORT_CALL
lou_getTypeformForEmphClass(const char *tableList, const char *emphClass) {
	int i;
	const TranslationTableHeader *table = _lou_getTranslationTable(tableList);
	if (!table) return 0;
	for (i = 0; table->emphClasses[i]; i++)
		if (strcmp(emphClass, table->emphClasses[i]) == 0) return italic << i;
	return 0;
}

static unsigned char *destSpacing = NULL;
static int sizeDestSpacing = 0;
static formtype *typebuf = NULL;
static unsigned int *wordBuffer = NULL;
static EmphasisInfo *emphasisBuffer = NULL;
static int sizeTypebuf = 0;
static widechar *passbuf[MAXPASSBUF] = { NULL };
static int sizePassbuf[MAXPASSBUF] = { 0 };
static int *posMapping1 = NULL;
static int sizePosMapping1 = 0;
static int *posMapping2 = NULL;
static int sizePosMapping2 = 0;
static int *posMapping3 = NULL;
static int sizePosMapping3 = 0;
void *EXPORT_CALL
_lou_allocMem(AllocBuf buffer, int index, int srcmax, int destmax) {
	if (srcmax < 1024) srcmax = 1024;
	if (destmax < 1024) destmax = 1024;
	switch (buffer) {
	case alloc_typebuf:
		if (destmax > sizeTypebuf) {
			if (typebuf != NULL) free(typebuf);
			// TODO: should this be srcmax?
			typebuf = malloc((destmax + 4) * sizeof(formtype));
			if (!typebuf) _lou_outOfMemory();
			sizeTypebuf = destmax;
		}
		return typebuf;

	case alloc_wordBuffer:

		if (wordBuffer != NULL) free(wordBuffer);
		wordBuffer = malloc((srcmax + 4) * sizeof(unsigned int));
		if (!wordBuffer) _lou_outOfMemory();
		return wordBuffer;

	case alloc_emphasisBuffer:

		if (emphasisBuffer != NULL) free(emphasisBuffer);
		emphasisBuffer = malloc((srcmax + 4) * sizeof(EmphasisInfo));
		if (!emphasisBuffer) _lou_outOfMemory();
		return emphasisBuffer;

	case alloc_destSpacing:
		if (destmax > sizeDestSpacing) {
			if (destSpacing != NULL) free(destSpacing);
			destSpacing = malloc(destmax + 4);
			if (!destSpacing) _lou_outOfMemory();
			sizeDestSpacing = destmax;
		}
		return destSpacing;
	case alloc_passbuf:
		if (index < 0 || index >= MAXPASSBUF) {
			_lou_logMessage(LOU_LOG_FATAL, "Index out of bounds: %d\n", index);
			exit(3);
		}
		if (destmax > sizePassbuf[index]) {
			if (passbuf[index] != NULL) free(passbuf[index]);
			passbuf[index] = malloc((destmax + 4) * CHARSIZE);
			if (!passbuf[index]) _lou_outOfMemory();
			sizePassbuf[index] = destmax;
		}
		return passbuf[index];
	case alloc_posMapping1: {
		int mapSize;
		if (srcmax >= destmax)
			mapSize = srcmax;
		else
			mapSize = destmax;
		if (mapSize > sizePosMapping1) {
			if (posMapping1 != NULL) free(posMapping1);
			posMapping1 = malloc((mapSize + 4) * sizeof(int));
			if (!posMapping1) _lou_outOfMemory();
			sizePosMapping1 = mapSize;
		}
	}
		return posMapping1;
	case alloc_posMapping2: {
		int mapSize;
		if (srcmax >= destmax)
			mapSize = srcmax;
		else
			mapSize = destmax;
		if (mapSize > sizePosMapping2) {
			if (posMapping2 != NULL) free(posMapping2);
			posMapping2 = malloc((mapSize + 4) * sizeof(int));
			if (!posMapping2) _lou_outOfMemory();
			sizePosMapping2 = mapSize;
		}
	}
		return posMapping2;
	case alloc_posMapping3: {
		int mapSize;
		if (srcmax >= destmax)
			mapSize = srcmax;
		else
			mapSize = destmax;
		if (mapSize > sizePosMapping3) {
			if (posMapping3 != NULL) free(posMapping3);
			posMapping3 = malloc((mapSize + 4) * sizeof(int));
			if (!posMapping3) _lou_outOfMemory();
			sizePosMapping3 = mapSize;
		}
	}
		return posMapping3;
	default:
		return NULL;
	}
}

void EXPORT_CALL
lou_free(void) {
	TranslationTableChainEntry *currentEntry;
	TranslationTableChainEntry *previousEntry;
	lou_logEnd();
	if (translationTableChain != NULL) {
		currentEntry = translationTableChain;
		while (currentEntry) {
			int i;
			TranslationTableHeader *t = (TranslationTableHeader *)currentEntry->table;
			for (i = 0; t->emphClasses[i]; i++) free(t->emphClasses[i]);
			if (t->characterClasses) deallocateCharacterClasses(t);
			if (t->ruleNames) deallocateRuleNames(t);
			free(t);
			previousEntry = currentEntry;
			currentEntry = currentEntry->next;
			free(previousEntry);
		}
		translationTableChain = NULL;
	}
	if (typebuf != NULL) free(typebuf);
	typebuf = NULL;
	if (wordBuffer != NULL) free(wordBuffer);
	wordBuffer = NULL;
	if (emphasisBuffer != NULL) free(emphasisBuffer);
	emphasisBuffer = NULL;
	sizeTypebuf = 0;
	if (destSpacing != NULL) free(destSpacing);
	destSpacing = NULL;
	sizeDestSpacing = 0;
	{
		int k;
		for (k = 0; k < MAXPASSBUF; k++) {
			if (passbuf[k] != NULL) free(passbuf[k]);
			passbuf[k] = NULL;
			sizePassbuf[k] = 0;
		}
	}
	if (posMapping1 != NULL) free(posMapping1);
	posMapping1 = NULL;
	sizePosMapping1 = 0;
	if (posMapping2 != NULL) free(posMapping2);
	posMapping2 = NULL;
	sizePosMapping2 = 0;
	if (posMapping3 != NULL) free(posMapping3);
	posMapping3 = NULL;
	sizePosMapping3 = 0;
	opcodeLengths[0] = 0;
}

const char *EXPORT_CALL
lou_version(void) {
	static const char *version = PACKAGE_VERSION;
	return version;
}

int EXPORT_CALL
lou_charSize(void) {
	return CHARSIZE;
}

int EXPORT_CALL
lou_compileString(const char *tableList, const char *inString) {
	TranslationTableHeader *table;
	DisplayTableHeader *displayTable;
	getTable(tableList, tableList, &table, &displayTable);
	if (!table) return 0;
	if (!compileString(inString, &table, &displayTable)) return 0;
	return 1;
}

int EXPORT_CALL
_lou_compileTranslationRule(const char *tableList, const char *inString) {
	TranslationTableHeader *table;
	getTable(tableList, NULL, &table, NULL);
	return compileString(inString, &table, NULL);
}

int EXPORT_CALL
_lou_compileDisplayRule(const char *tableList, const char *inString) {
	DisplayTableHeader *table;
	getTable(NULL, tableList, NULL, &table);
	return compileString(inString, NULL, &table);
}

/**
 * This procedure provides a target for cals that serve as breakpoints
 * for gdb.
 */
// char *EXPORT_CALL
// lou_getTablePaths (void)
// {
//   static char paths[MAXSTRING];
//   static char scratchBuf[MAXSTRING];
//   char *pathList;
//   strcpy (paths, tablePath);
//   strcat (paths, ",");
//   pathList = getenv ("LOUIS_TABLEPATH");
//   if (pathList)
//     {
//       strcat (paths, pathList);
//       strcat (paths, ",");
//     }
//   pathList = getcwd (scratchBuf, MAXSTRING);
//   if (pathList)
//     {
//       strcat (paths, pathList);
//       strcat (paths, ",");
//     }
//   pathList = lou_getDataPath ();
//   if (pathList)
//     {
//       strcat (paths, pathList);
//       strcat (paths, ",");
//     }
// #ifdef _WIN32
//   strcpy (paths, lou_getProgramPath ());
//   strcat (paths, "\\share\\liblouss\\tables\\");
// #else
//   strcpy (paths, TABLESDIR);
// #endif
//   return paths;
// }
