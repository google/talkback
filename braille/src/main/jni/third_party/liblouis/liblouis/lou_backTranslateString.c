/* liblouis Braille Translation and Back-Translation Library

   Based on the Linux screenreader BRLTTY, copyright (C) 1999-2006 by The
   BRLTTY Team

   Copyright (C) 2004, 2005, 2006 ViewPlus Technologies, Inc. www.viewplus.com
   Copyright (C) 2004, 2005, 2006 JJB Software, Inc. www.jjb-software.com

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
 * @brief Translate from braille
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "internal.h"

typedef struct {
	int size;
	widechar **buffers;
	int *inUse;
	widechar *(*alloc)(int index, int length);
	void (*free)(widechar *);
} StringBufferPool;

static widechar *
allocStringBuffer(int index, int length) {
	return _lou_allocMem(alloc_passbuf, index, 0, length);
}

static const StringBufferPool *stringBufferPool = NULL;

static void
initStringBufferPool() {
	static widechar *stringBuffers[MAXPASSBUF] = { NULL };
	static int stringBuffersInUse[MAXPASSBUF] = { 0 };
	StringBufferPool *pool = malloc(sizeof(StringBufferPool));
	pool->size = MAXPASSBUF;
	pool->buffers = stringBuffers;
	pool->inUse = stringBuffersInUse;
	pool->alloc = &allocStringBuffer;
	pool->free = NULL;
	stringBufferPool = pool;
}

static int
getStringBuffer(int length) {
	int i;
	for (i = 0; i < stringBufferPool->size; i++) {
		if (!stringBufferPool->inUse[i]) {
			stringBufferPool->buffers[i] = stringBufferPool->alloc(i, length);
			stringBufferPool->inUse[i] = 1;
			return i;
		}
	}
	_lou_outOfMemory();
	return -1;
}

static int
releaseStringBuffer(int idx) {
	if (idx >= 0 && idx < stringBufferPool->size) {
		int inUse = stringBufferPool->inUse[idx];
		if (inUse && stringBufferPool->free)
			stringBufferPool->free(stringBufferPool->buffers[idx]);
		stringBufferPool->inUse[idx] = 0;
		return inUse;
	}
	return 0;
}

typedef struct {
	int bufferIndex;
	const widechar *chars;
	int length;
} InString;

typedef struct {
	int bufferIndex;
	widechar *chars;
	int maxlength;
	int length;
} OutString;

typedef struct {
	int startMatch;
	int startReplace;
	int endReplace;
	int endMatch;
} PassRuleMatch;

static int
backTranslateString(const TranslationTableHeader *table, int mode, int currentPass,
		const InString *input, OutString *output, char *spacebuf, int *posMapping,
		int *realInlen, int *cursorPosition, int *cursorStatus,
		const TranslationTableRule **appliedRules, int *appliedRulesCount,
		int maxAppliedRules);
static int
makeCorrections(const TranslationTableHeader *table, int mode, int currentPass,
		const InString *input, OutString *output, int *posMapping, int *realInlen,
		int *cursorPosition, int *cursorStatus, const TranslationTableRule **appliedRules,
		int *appliedRulesCount, int maxAppliedRules);
static int
translatePass(const TranslationTableHeader *table, int mode, int currentPass,
		const InString *input, OutString *output, int *posMapping, int *realInlen,
		int *cursorPosition, int *cursorStatus, const TranslationTableRule **appliedRules,
		int *appliedRulesCount, int maxAppliedRules);
static void
passSelectRule(const TranslationTableHeader *table, int pos, int currentPass,
		const InString *input, TranslationTableOpcode *currentOpcode,
		const TranslationTableRule **currentRule, const widechar **passInstructions,
		int *passIC, PassRuleMatch *match);

int EXPORT_CALL
lou_backTranslateString(const char *tableList, const widechar *inbuf, int *inlen,
		widechar *outbuf, int *outlen, formtype *typeform, char *spacing, int modex) {
	return lou_backTranslate(tableList, inbuf, inlen, outbuf, outlen, typeform, spacing,
			NULL, NULL, NULL, modex);
}

int EXPORT_CALL
lou_backTranslate(const char *tableList, const widechar *inbuf, int *inlen,
		widechar *outbuf, int *outlen, formtype *typeform, char *spacing, int *outputPos,
		int *inputPos, int *cursorPos, int modex) {
	return _lou_backTranslate(tableList, tableList, inbuf, inlen, outbuf, outlen,
			typeform, spacing, outputPos, inputPos, cursorPos, modex, NULL, NULL);
}

int EXPORT_CALL
_lou_backTranslate(const char *tableList, const char *displayTableList,
		const widechar *inbuf, int *inlen, widechar *outbuf, int *outlen,
		formtype *typeform, char *spacing, int *outputPos, int *inputPos, int *cursorPos,
		int mode, const TranslationTableRule **rules, int *rulesLen) {
	const TranslationTableHeader *table;
	const DisplayTableHeader *displayTable;
	InString input;
	OutString output;
	unsigned char *typebuf = NULL;
	char *spacebuf;
	// posMapping contains position mapping info between the output of the current pass
	// and the initial input. It is 1 longer than the (consumed) input. The values are
	// monotonically increasing and can range between -1 and the output length. At the end
	// the position info is passed to the user as an inputPos and outputPos array.
	// inputPos has the length of the final output and has values ranging from 0 to
	// inlen-1. outputPos has the length of the (consumed) initial input and has values
	// ranging from 0 to outlen-1.
	int *posMapping = NULL;
	int *posMapping1;
	int *posMapping2;
	int *posMapping3;
	int cursorPosition;
	int cursorStatus;
	const TranslationTableRule **appliedRules;
	int maxAppliedRules;
	int appliedRulesCount;
	int k;
	int goodTrans = 1;
	int idx;
	if (tableList == NULL || inbuf == NULL || inlen == NULL || outbuf == NULL ||
			outlen == NULL)
		return 0;
	if (displayTableList == NULL) displayTableList = tableList;
	_lou_getTable(tableList, displayTableList, &table, &displayTable);
	if (table == NULL) return 0;

	if (!_lou_isValidMode(mode))
		_lou_logMessage(LOU_LOG_ERROR, "Invalid mode parameter: %d", mode);

	if (!stringBufferPool) initStringBufferPool();
	for (idx = 0; idx < stringBufferPool->size; idx++) releaseStringBuffer(idx);
	{
		widechar *passbuf1;
		int srcmax;
		k = 0;
		while (k < *inlen && inbuf[k]) k++;
		srcmax = k;
		idx = getStringBuffer(srcmax);
		passbuf1 = stringBufferPool->buffers[idx];
		for (k = 0; k < srcmax; k++)
			if ((mode & dotsIO))
				passbuf1[k] = inbuf[k] | LOU_DOTS;
			else
				passbuf1[k] = _lou_getDotsForChar(inbuf[k], displayTable);
		passbuf1[srcmax] = _lou_getDotsForChar(' ', displayTable);
		input = (InString){ .chars = passbuf1, .length = srcmax, .bufferIndex = idx };
	}
	idx = getStringBuffer(*outlen);
	output = (OutString){ .chars = stringBufferPool->buffers[idx],
		.maxlength = *outlen,
		.length = 0,
		.bufferIndex = idx };
	typebuf = (unsigned char *)typeform;
	spacebuf = spacing;
	if (outputPos != NULL)
		for (k = 0; k < input.length; k++) outputPos[k] = -1;
	if (cursorPos != NULL)
		cursorPosition = *cursorPos;
	else
		cursorPosition = -1;
	cursorStatus = 0;
	if (typebuf != NULL) memset(typebuf, '0', *outlen);
	if (spacebuf != NULL) memset(spacebuf, '*', *outlen);
	if (!(posMapping1 = _lou_allocMem(alloc_posMapping1, 0, input.length, *outlen)))
		return 0;
	if (table->numPasses > 1 || table->corrections) {
		if (!(posMapping2 = _lou_allocMem(alloc_posMapping2, 0, input.length, *outlen)))
			return 0;
		if (!(posMapping3 = _lou_allocMem(alloc_posMapping3, 0, input.length, *outlen)))
			return 0;
	}
	appliedRulesCount = 0;
	if (rules != NULL && rulesLen != NULL) {
		appliedRules = rules;
		maxAppliedRules = *rulesLen;
	} else {
		appliedRules = NULL;
		maxAppliedRules = 0;
	}

	posMapping = posMapping1;
	int currentPass = table->numPasses;
	int lastPass = table->corrections ? 0 : 1;
	int *passPosMapping = posMapping;
	while (1) {
		int realInlen;
		switch (currentPass) {
		case 1:
			goodTrans = backTranslateString(table, mode, currentPass, &input, &output,
					spacebuf, passPosMapping, &realInlen, &cursorPosition, &cursorStatus,
					appliedRules, &appliedRulesCount, maxAppliedRules);
			break;
		case 0:
			goodTrans = makeCorrections(table, mode, currentPass, &input, &output,
					passPosMapping, &realInlen, &cursorPosition, &cursorStatus,
					appliedRules, &appliedRulesCount, maxAppliedRules);
			break;
		default:
			goodTrans = translatePass(table, mode, currentPass, &input, &output,
					passPosMapping, &realInlen, &cursorPosition, &cursorStatus,
					appliedRules, &appliedRulesCount, maxAppliedRules);
			break;
		}
		passPosMapping[realInlen] = output.length;
		if (passPosMapping == posMapping) {
			passPosMapping = posMapping2;
			if (realInlen < input.length) *inlen = realInlen;
		} else {
			int *prevPosMapping = posMapping3;
			memcpy((int *)prevPosMapping, posMapping, (*inlen + 1) * sizeof(int));
			for (k = 0; k <= *inlen; k++) {
				if (prevPosMapping[k] < 0)
					posMapping[k] = passPosMapping[0];
				else if (prevPosMapping[k] < realInlen)
					posMapping[k] = passPosMapping[prevPosMapping[k]];
				else if (prevPosMapping[k] == realInlen) {
					// outputPos is allowed to point to right after the last output
					// character if the input character was deleted
					if (realInlen < input.length) {
						// however if there was back-tracking, we know that this is not
						// the case
						*inlen = k;
						posMapping[k] = output.length;
						break;
					} else
						posMapping[k] = passPosMapping[prevPosMapping[k]];
				} else {
					// this means there has been back-tracking to a point within a segment
					// that was atomic in the previous pass
					// it is not clear what should happen in this case
					*inlen = k;
					posMapping[k] = output.length;
					break;
				}
			}
		}
		currentPass--;
		if (currentPass >= lastPass && goodTrans) {
			releaseStringBuffer(input.bufferIndex);
			input = (InString){ .chars = output.chars,
				.length = output.length,
				.bufferIndex = output.bufferIndex };
			idx = getStringBuffer(*outlen);
			output = (OutString){ .chars = stringBufferPool->buffers[idx],
				.maxlength = *outlen,
				.length = 0,
				.bufferIndex = idx };
			continue;
		}
		break;
	}
	if (goodTrans) {
		for (k = 0; k < output.length; k++) outbuf[k] = output.chars[k];
		*outlen = output.length;
		if (inputPos != NULL) {
			int inpos = -1;
			int outpos = -1;
			for (k = 0; k < *inlen; k++)
				if (posMapping[k] > outpos) {
					while (outpos < posMapping[k]) {
						if (outpos >= 0 && outpos < *outlen)
							inputPos[outpos] = inpos < 0 ? 0 : inpos;
						outpos++;
					}
					inpos = k;
				}
			if (outpos < 0) outpos = 0;
			while (outpos < *outlen) inputPos[outpos++] = inpos;
		}
		if (outputPos != NULL) {
			for (k = 0; k < *inlen; k++)
				if (posMapping[k] < 0)
					outputPos[k] = 0;
				else if (posMapping[k] > *outlen - 1)
					outputPos[k] = *outlen - 1;
				else
					outputPos[k] = posMapping[k];
		}
	}
	if (cursorPos != NULL && *cursorPos != -1) {
		if (outputPos != NULL)
			*cursorPos = outputPos[*cursorPos];
		else
			*cursorPos = cursorPosition;
	}
	if (rulesLen != NULL) *rulesLen = appliedRulesCount;
	return goodTrans;
}

static TranslationTableCharacter *
getChar(widechar c, const TranslationTableHeader *table) {
	static TranslationTableCharacter notFound = { 0, 0, 0, CTC_Space, 32, 32, 32 };
	unsigned long int makeHash = _lou_charHash(c);
	TranslationTableOffset bucket = table->characters[makeHash];
	while (bucket) {
		TranslationTableCharacter *character =
				(TranslationTableCharacter *)&table->ruleArea[bucket];
		if (character->realchar == c) return character;
		bucket = character->next;
	}
	notFound.realchar = notFound.uppercase = notFound.lowercase = c;
	return &notFound;
}

static TranslationTableCharacter *
getDots(widechar c, const TranslationTableHeader *table) {
	static TranslationTableCharacter notFound = { 0, 0, 0, CTC_Space, LOU_DOTS, LOU_DOTS,
		LOU_DOTS };
	unsigned long int makeHash = _lou_charHash(c);
	TranslationTableOffset bucket = table->dots[makeHash];
	while (bucket) {
		TranslationTableCharacter *character =
				(TranslationTableCharacter *)&table->ruleArea[bucket];
		if (character->realchar == c) return character;
		bucket = character->next;
	}
	notFound.realchar = notFound.uppercase = notFound.lowercase = c;
	return &notFound;
}

static int
checkDotsAttr(const widechar d, const TranslationTableCharacterAttributes a,
		const TranslationTableHeader *table) {
	static widechar prevd = 0;
	static TranslationTableCharacterAttributes preva = 0;
	if (d != prevd) {
		preva = (getDots(d, table))->attributes;
		prevd = d;
	}
	return ((preva & a) ? 1 : 0);
}

static int
compareDots(const widechar *address1, const widechar *address2, int count) {
	int k;
	if (!count) return 0;
	for (k = 0; k < count; k++)
		if (address1[k] != address2[k]) return 0;
	return 1;
}

static void
back_setBefore(const TranslationTableHeader *table, OutString *output,
		TranslationTableCharacterAttributes *beforeAttributes) {
	widechar before = (output->length == 0) ? ' ' : output->chars[output->length - 1];
	*beforeAttributes = (getChar(before, table))->attributes;
}

static void
back_setAfter(int length, const TranslationTableHeader *table, int pos,
		const InString *input, TranslationTableCharacterAttributes *afterAttributes) {
	widechar after = (pos + length < input->length) ? input->chars[pos + length] : ' ';
	*afterAttributes = (getDots(after, table))->attributes;
}

static int
isBegWord(const TranslationTableHeader *table, OutString *output) {
	/* See if this is really the beginning of a word. Look at what has
	 * already been translated. */
	int k;
	if (output->length == 0) return 1;
	for (k = output->length - 1; k >= 0; k--) {
		const TranslationTableCharacter *ch = getChar(output->chars[k], table);
		if (ch->attributes & CTC_Space) break;
		if (ch->attributes & (CTC_Letter | CTC_Digit | CTC_Math | CTC_Sign)) return 0;
	}
	return 1;
}

static int
isEndWord(const TranslationTableHeader *table, int pos, int mode, const InString *input,
		int currentDotslen) {
	if (mode & partialTrans) return 0;
	/* See if this is really the end of a word. */
	int k;
	const TranslationTableCharacter *dots;
	TranslationTableOffset testRuleOffset;
	TranslationTableRule *testRule;
	for (k = pos + currentDotslen; k < input->length; k++) {
		int postpuncFound = 0;
		int TranslationFound = 0;
		dots = getDots(input->chars[k], table);
		testRuleOffset = dots->otherRules;
		if (dots->attributes & CTC_Space) break;
		if (dots->attributes & CTC_Letter) return 0;
		while (testRuleOffset) {
			testRule = (TranslationTableRule *)&table->ruleArea[testRuleOffset];
			/* #360: Don't treat begword/midword as definite translations here
			 * because we don't know whether they apply yet. Subsequent
			 * input will allow us to determine whether the word continues.
			 */
			if (testRule->charslen > 1 && testRule->opcode != CTO_BegWord &&
					testRule->opcode != CTO_MidWord)
				TranslationFound = 1;
			if (testRule->opcode == CTO_PostPunc) postpuncFound = 1;
			if (testRule->opcode == CTO_Hyphen) return 1;
			testRuleOffset = testRule->dotsnext;
		}
		if (TranslationFound && !postpuncFound) return 0;
	}
	return 1;
}
static int
findBrailleIndicatorRule(TranslationTableOffset offset,
		const TranslationTableHeader *table, int *currentDotslen,
		TranslationTableOpcode *currentOpcode, const TranslationTableRule **currentRule) {
	if (!offset) return 0;
	*currentRule = (TranslationTableRule *)&table->ruleArea[offset];
	*currentOpcode = (*currentRule)->opcode;
	*currentDotslen = (*currentRule)->dotslen;
	return 1;
}

static int
handleMultind(const TranslationTableHeader *table, int *currentDotslen,
		TranslationTableOpcode *currentOpcode, const TranslationTableRule **currentRule,
		int *doingMultind, const TranslationTableRule *multindRule) {
	/* Handle multille braille indicators */
	int found = 0;
	if (!*doingMultind) return 0;
	switch (multindRule->charsdots[multindRule->charslen - *doingMultind]) {
	case CTO_CapsLetterRule:  // FIXME: make sure this works
		found = findBrailleIndicatorRule(table->emphRules[capsRule][letterOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	// NOTE:  following fixme is based on the names at the time of
	//        commit f22f91eb510cb4eef33dfb4950a297235dd2f9f1.
	// FIXME: the next two opcodes were begcaps/endcaps,
	//        and they were aliased to opcodes capsword/capswordstop.
	//        However, the table attributes they use are
	//        table->beginCapitalSign and table->endCapitalSign.
	//        These are actually compiled with firstlettercaps/lastlettercaps.
	//        Which to use here?
	case CTO_BegCapsWordRule:
		found = findBrailleIndicatorRule(table->emphRules[capsRule][begWordOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndCapsWordRule:
		found = findBrailleIndicatorRule(table->emphRules[capsRule][endWordOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_LetterSign:
		found = findBrailleIndicatorRule(
				table->letterSign, table, currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_NoContractSign:
		found = findBrailleIndicatorRule(
				table->noContractSign, table, currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_NumberSign:
		found = findBrailleIndicatorRule(
				table->numberSign, table, currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndEmph1PhraseBeforeRule:
		found = findBrailleIndicatorRule(
				table->emphRules[emph1Rule][endPhraseBeforeOffset], table, currentDotslen,
				currentOpcode, currentRule);
		break;
	case CTO_BegEmph1Rule:
		found = findBrailleIndicatorRule(table->emphRules[emph1Rule][begOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndEmph1Rule:
		found = findBrailleIndicatorRule(table->emphRules[emph1Rule][endOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndEmph2PhraseBeforeRule:
		found = findBrailleIndicatorRule(
				table->emphRules[emph2Rule][endPhraseBeforeOffset], table, currentDotslen,
				currentOpcode, currentRule);
		break;
	case CTO_BegEmph2Rule:
		found = findBrailleIndicatorRule(table->emphRules[emph2Rule][begOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndEmph2Rule:
		found = findBrailleIndicatorRule(table->emphRules[emph2Rule][endOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndEmph3PhraseBeforeRule:
		found = findBrailleIndicatorRule(
				table->emphRules[emph3Rule][endPhraseBeforeOffset], table, currentDotslen,
				currentOpcode, currentRule);
		break;
	case CTO_BegEmph3Rule:
		found = findBrailleIndicatorRule(table->emphRules[emph3Rule][begOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndEmph3Rule:
		found = findBrailleIndicatorRule(table->emphRules[emph3Rule][endOffset], table,
				currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_BegComp:
		found = findBrailleIndicatorRule(
				table->begComp, table, currentDotslen, currentOpcode, currentRule);
		break;
	case CTO_EndComp:
		found = findBrailleIndicatorRule(
				table->endComp, table, currentDotslen, currentOpcode, currentRule);
		break;
	default:
		found = 0;
		break;
	}
	(*doingMultind)--;
	return found;
}

static int
back_passDoTest(const TranslationTableHeader *table, int pos, const InString *input,
		TranslationTableOpcode currentOpcode, const TranslationTableRule *currentRule,
		const widechar **passInstructions, int *passIC, PassRuleMatch *match);
static int
back_passDoAction(const TranslationTableHeader *table, int *pos, int mode,
		const InString *input, OutString *output, int *posMapping, int *cursorPosition,
		int *cursorStatus, int *nextUpper, int allUpper, int allUpperPhrase,
		TranslationTableOpcode currentOpcode, const TranslationTableRule *currentRule,
		const widechar *passInstructions, int passIC, PassRuleMatch match);

static int
findBackPassRule(const TranslationTableHeader *table, int pos, int currentPass,
		const InString *input, TranslationTableOpcode *currentOpcode,
		const TranslationTableRule **currentRule, const widechar **passInstructions,
		int *passIC, PassRuleMatch *match) {
	TranslationTableOffset ruleOffset;
	ruleOffset = table->backPassRules[currentPass];

	while (ruleOffset) {
		*currentRule = (TranslationTableRule *)&table->ruleArea[ruleOffset];
		*currentOpcode = (*currentRule)->opcode;

		switch (*currentOpcode) {
		case CTO_Correct:
			if (currentPass != 0) goto NEXT_RULE;
			break;
		case CTO_Context:
			if (currentPass != 1) goto NEXT_RULE;
			break;
		case CTO_Pass2:
			if (currentPass != 2) goto NEXT_RULE;
			break;
		case CTO_Pass3:
			if (currentPass != 3) goto NEXT_RULE;
			break;
		case CTO_Pass4:
			if (currentPass != 4) goto NEXT_RULE;
			break;
		default:
			goto NEXT_RULE;
		}

		if (back_passDoTest(table, pos, input, *currentOpcode, *currentRule,
					passInstructions, passIC, match))
			return 1;

	NEXT_RULE:
		ruleOffset = (*currentRule)->dotsnext;
	}

	return 0;
}

static void
back_selectRule(const TranslationTableHeader *table, int pos, int mode,
		const InString *input, OutString *output, int itsANumber, int itsALetter,
		int *currentDotslen, TranslationTableOpcode *currentOpcode,
		const TranslationTableRule **currentRule, TranslationTableOpcode previousOpcode,
		int *doingMultind, const TranslationTableRule **multindRule,
		TranslationTableCharacterAttributes beforeAttributes,
		const widechar **passInstructions, int *passIC, PassRuleMatch *patternMatch) {
	/* check for valid back-translations */
	int length = input->length - pos;
	TranslationTableOffset ruleOffset = 0;
	static TranslationTableRule pseudoRule = { 0 };
	unsigned long int makeHash = 0;
	const TranslationTableCharacter *dots = getDots(input->chars[pos], table);
	int tryThis;
	if (handleMultind(table, currentDotslen, currentOpcode, currentRule, doingMultind,
				*multindRule))
		return;
	for (tryThis = 0; tryThis < 3; tryThis++) {
		switch (tryThis) {
		case 0:
			if (length < 2 || (itsANumber && (dots->attributes & CTC_LitDigit))) break;
			/* Hash function optimized for backward translation */
			makeHash = (unsigned long int)dots->realchar << 8;
			makeHash +=
					(unsigned long int)(getDots(input->chars[pos + 1], table))->realchar;
			makeHash %= HASHNUM;
			ruleOffset = table->backRules[makeHash];
			break;
		case 1:
			if (!(length >= 1)) break;
			length = 1;
			ruleOffset = dots->otherRules;
			break;
		case 2: /* No rule found */
			*currentRule = &pseudoRule;
			*currentOpcode = pseudoRule.opcode = CTO_None;
			*currentDotslen = pseudoRule.dotslen = 1;
			pseudoRule.charsdots[0] = input->chars[pos];
			pseudoRule.charslen = 0;
			return;
			break;
		}
		while (ruleOffset) {
			const widechar *currentDots;
			*currentRule = (TranslationTableRule *)&table->ruleArea[ruleOffset];
			*currentOpcode = (*currentRule)->opcode;
			if (*currentOpcode == CTO_Context) {
				currentDots = &(*currentRule)->charsdots[0];
				*currentDotslen = (*currentRule)->charslen;
			} else {
				currentDots = &(*currentRule)->charsdots[(*currentRule)->charslen];
				*currentDotslen = (*currentRule)->dotslen;
			}
			if (((*currentDotslen <= length) &&
						compareDots(&input->chars[pos], currentDots, *currentDotslen))) {
				TranslationTableCharacterAttributes afterAttributes;
				/* check this rule */
				back_setAfter(*currentDotslen, table, pos, input, &afterAttributes);
				if ((!((*currentRule)->after & ~CTC_EmpMatch) ||
							(beforeAttributes & (*currentRule)->after)) &&
						(!((*currentRule)->before & ~CTC_EmpMatch) ||
								(afterAttributes & (*currentRule)->before))) {
					switch (*currentOpcode) { /* check validity of this Translation */
					case CTO_Context:
						if (back_passDoTest(table, pos, input, *currentOpcode,
									*currentRule, passInstructions, passIC, patternMatch))
							return;
						break;
					case CTO_Space:
					case CTO_Digit:
					case CTO_Letter:
					case CTO_UpperCase:
					case CTO_LowerCase:
					case CTO_Punctuation:
					case CTO_Math:
					case CTO_Sign:
					case CTO_ExactDots:
					case CTO_Repeated:
					case CTO_Replace:
					case CTO_Hyphen:
						return;
					case CTO_LitDigit:
						if (itsANumber) return;
						break;
					case CTO_CapsLetterRule:
					case CTO_BegCapsRule:
					case CTO_EndCapsRule:
					case CTO_BegCapsWordRule:
					case CTO_EndCapsWordRule:
					case CTO_BegEmph1Rule:
					case CTO_EndEmph1Rule:
					case CTO_BegEmph2Rule:
					case CTO_EndEmph2Rule:
					case CTO_BegEmph3Rule:
					case CTO_EndEmph3Rule:
					case CTO_NumberRule:
					case CTO_BegCompRule:
					case CTO_EndCompRule:
						return;
					case CTO_LetterRule:
					case CTO_NoContractRule:
						// BF: This is just a heuristic test. During forward translation,
						// the
						// nocontractsign is inserted either when in numeric mode and the
						// next
						// character is not numeric (CTC_Digit | CTC_LitDigit |
						// CTC_NumericMode | CTC_MidEndNumericMode),
						// or when a "contraction" rule is matched and the characters are
						// preceded and followed by space or punctuation (CTC_Space |
						// CTC_Punctuation).
						if (!(beforeAttributes & CTC_Letter) &&
								(afterAttributes & (CTC_Letter | CTC_Sign)))
							return;
						break;
					case CTO_MultInd:
						*doingMultind = *currentDotslen;
						*multindRule = *currentRule;
						if (handleMultind(table, currentDotslen, currentOpcode,
									currentRule, doingMultind, *multindRule))
							return;
						break;
					case CTO_LargeSign:
						return;
					case CTO_WholeWord:
						if (mode & partialTrans) break;
						if (itsALetter || itsANumber) break;
					case CTO_Contraction:
						if ((beforeAttributes & (CTC_Space | CTC_Punctuation)) &&
								((afterAttributes & CTC_Space) ||
										isEndWord(table, pos, mode, input,
												*currentDotslen)))
							return;
						break;
					case CTO_LowWord:
						if (mode & partialTrans) break;
						if ((beforeAttributes & CTC_Space) &&
								(afterAttributes & CTC_Space) &&
								(previousOpcode != CTO_JoinableWord))
							return;
						break;
					case CTO_JoinNum:
					case CTO_JoinableWord:
						if ((beforeAttributes & (CTC_Space | CTC_Punctuation)) &&
								(!(afterAttributes & CTC_Space) || mode & partialTrans))
							return;
						break;
					case CTO_SuffixableWord:
						if (beforeAttributes & (CTC_Space | CTC_Punctuation)) return;
						break;
					case CTO_PrefixableWord:
						if ((beforeAttributes &
									(CTC_Space | CTC_Letter | CTC_Punctuation)) &&
								isEndWord(table, pos, mode, input, *currentDotslen))
							return;
						break;
					case CTO_BegWord:
						if ((beforeAttributes & (CTC_Space | CTC_Punctuation)) &&
								(!isEndWord(table, pos, mode, input, *currentDotslen)))
							return;
						break;
					case CTO_BegMidWord:
						if ((beforeAttributes &
									(CTC_Letter | CTC_Space | CTC_Punctuation)) &&
								(!isEndWord(table, pos, mode, input, *currentDotslen)))
							return;
						break;
					case CTO_PartWord:
						if (!(beforeAttributes & CTC_LitDigit) &&
								(beforeAttributes & CTC_Letter ||
										!isEndWord(table, pos, mode, input,
												*currentDotslen)))
							return;
						break;
					case CTO_MidWord:
						if (beforeAttributes & CTC_Letter &&
								!isEndWord(table, pos, mode, input, *currentDotslen))
							return;
						break;
					case CTO_MidEndWord:
						if ((beforeAttributes & CTC_Letter)) return;
						break;
					case CTO_EndWord:
						if ((beforeAttributes & CTC_Letter) &&
								isEndWord(table, pos, mode, input, *currentDotslen))
							return;
						break;
					case CTO_BegNum:
						if (beforeAttributes & (CTC_Space | CTC_Punctuation) &&
								(afterAttributes & (CTC_LitDigit | CTC_Sign)))
							return;
						break;
					case CTO_MidNum:
						if (beforeAttributes & CTC_Digit &&
								afterAttributes & CTC_LitDigit)
							return;
						break;
					case CTO_EndNum:
						if (itsANumber && !(afterAttributes & CTC_LitDigit)) return;
						break;
					case CTO_DecPoint:
						if (afterAttributes & (CTC_Digit | CTC_LitDigit)) return;
						break;
					case CTO_PrePunc:
						if (isBegWord(table, output)) return;
						break;

					case CTO_PostPunc:
						if (isEndWord(table, pos, mode, input, *currentDotslen)) return;
						break;
					case CTO_Always:
						if ((beforeAttributes & CTC_LitDigit) &&
								(afterAttributes & CTC_LitDigit) &&
								(*currentRule)->charslen > 1)
							break;
						return;

					case CTO_BackMatch: {
						widechar *patterns, *pattern;

						// if(dontContract || (mode & noContractions))
						//	break;
						// if(checkEmphasisChange(0))
						//	break;

						patterns = (widechar *)&table->ruleArea[(*currentRule)->patterns];

						/* check before pattern */
						pattern = &patterns[1];
						if (!_lou_pattern_check(
									input->chars, pos - 1, -1, -1, pattern, table))
							break;

						/* check after pattern */
						pattern = &patterns[patterns[0]];
						if (!_lou_pattern_check(input->chars,
									pos + (*currentRule)->dotslen, input->length, 1,
									pattern, table))
							break;

						return;
					}
					default:
						break;
					}
				}
			} /* Done with checking this rule */
			ruleOffset = (*currentRule)->dotsnext;
		}
	}
}

static int
putchars(const widechar *chars, int count, const TranslationTableHeader *table,
		OutString *output, int *nextUpper, int allUpper, int allUpperPhrase) {
	int k = 0;
	if (!count || (output->length + count) > output->maxlength) return 0;
	if (*nextUpper) {
		output->chars[(output->length)++] = (getChar(chars[k++], table))->uppercase;
		*nextUpper = 0;
	}
	if (!allUpper && !allUpperPhrase) {
		memcpy(&output->chars[output->length], &chars[k], CHARSIZE * (count - k));
		output->length += count - k;
	} else
		for (; k < count; k++)
			output->chars[(output->length)++] = (getChar(chars[k], table))->uppercase;
	return 1;
}

static int
back_updatePositions(const widechar *outChars, int inLength, int outLength,
		const TranslationTableHeader *table, int pos, const InString *input,
		OutString *output, int *posMapping, int *cursorPosition, int *cursorStatus,
		int *nextUpper, int allUpper, int allUpperPhrase) {
	int k;
	if ((output->length + outLength) > output->maxlength ||
			(pos + inLength) > input->length)
		return 0;
	if (!*cursorStatus && *cursorPosition >= pos && *cursorPosition < (pos + inLength)) {
		*cursorPosition = output->length + outLength / 2;
		*cursorStatus = 1;
	}
	for (k = 0; k < inLength; k++) posMapping[pos + k] = output->length;
	return putchars(
			outChars, outLength, table, output, nextUpper, allUpper, allUpperPhrase);
}

static int
undefinedDots(widechar dots, int mode, OutString *output, int pos, int *posMapping) {
	posMapping[pos] = output->length;
	if (mode & noUndefined) return 1;

	/* Print out dot numbers */
	const char *buffer = _lou_unknownDots(dots);
	size_t buflen = strlen(buffer);
	if ((output->length + buflen) > output->maxlength) return 0;

	for (unsigned int k = 0; k < buflen; k += 1) {
		output->chars[output->length++] = buffer[k];
	}

	return 1;
}

static int
putCharacter(widechar dots, const TranslationTableHeader *table, int pos, int mode,
		const InString *input, OutString *output, int *posMapping, int *cursorPosition,
		int *cursorStatus, int *nextUpper, int allUpper, int allUpperPhrase) {
	/* Output character(s) corresponding to a Unicode braille Character */
	TranslationTableOffset offset = (getDots(dots, table))->definitionRule;
	if (offset) {
		const TranslationTableRule *rule =
				(TranslationTableRule *)&table->ruleArea[offset];
		return back_updatePositions(&rule->charsdots[0], rule->dotslen, rule->charslen,
				table, pos, input, output, posMapping, cursorPosition, cursorStatus,
				nextUpper, allUpper, allUpperPhrase);
	}
	return undefinedDots(dots, mode, output, pos, posMapping);
}

static int
putCharacters(const widechar *characters, int count, const TranslationTableHeader *table,
		int pos, int mode, const InString *input, OutString *output, int *posMapping,
		int *cursorPosition, int *cursorStatus, int *nextUpper, int allUpper,
		int allUpperPhrase) {
	int k;
	for (k = 0; k < count; k++)
		if (!putCharacter(characters[k], table, pos, mode, input, output, posMapping,
					cursorPosition, cursorStatus, nextUpper, allUpper, allUpperPhrase))
			return 0;
	return 1;
}

static int
insertSpace(const TranslationTableHeader *table, int pos, const InString *input,
		OutString *output, char *spacebuf, int *posMapping, int *cursorPosition,
		int *cursorStatus, int *nextUpper, int allUpper, int allUpperPhrase) {
	widechar c = ' ';
	if (!back_updatePositions(&c, 1, 1, table, pos, input, output, posMapping,
				cursorPosition, cursorStatus, nextUpper, allUpper, allUpperPhrase))
		return 0;
	if (spacebuf) spacebuf[output->length - 1] = '1';
	return 1;
}

static int
compareChars(const widechar *address1, const widechar *address2, int count,
		const TranslationTableHeader *table) {
	int k;
	if (!count) return 0;
	for (k = 0; k < count; k++)
		if ((getChar(address1[k], table))->lowercase !=
				(getChar(address2[k], table))->lowercase)
			return 0;
	return 1;
}

static int
makeCorrections(const TranslationTableHeader *table, int mode, int currentPass,
		const InString *input, OutString *output, int *posMapping, int *realInlen,
		int *cursorPosition, int *cursorStatus, const TranslationTableRule **appliedRules,
		int *appliedRulesCount, int maxAppliedRules) {
	int pos;
	int posIncremented = 1;
	int nextUpper = 0;
	int allUpper = 0;
	int allUpperPhrase = 0;
	if (!table->corrections) return 1;
	pos = 0;
	output->length = 0;
	_lou_resetPassVariables();
	while (pos < input->length) {
		int posBefore = pos;
		TranslationTableOpcode currentOpcode;
		const TranslationTableRule *currentRule; /* pointer to current rule in table */
		const widechar *passInstructions;
		int passIC; /* Instruction counter */
		PassRuleMatch patternMatch;
		int length = input->length - pos;
		const TranslationTableCharacter *character = getChar(input->chars[pos], table);
		const TranslationTableCharacter *character2;
		int tryThis = 0;
		if (!(posIncremented &&
					findBackPassRule(table, pos, currentPass, input, &currentOpcode,
							&currentRule, &passInstructions, &passIC, &patternMatch)))
			while (tryThis < 3) {
				TranslationTableOffset ruleOffset = 0;
				unsigned long int makeHash = 0;
				switch (tryThis) {
				case 0:
					if (!(length >= 2)) break;
					makeHash = (unsigned long int)character->lowercase << 8;
					character2 = getChar(input->chars[pos + 1], table);
					makeHash += (unsigned long int)character2->lowercase;
					makeHash %= HASHNUM;
					ruleOffset = table->forRules[makeHash];
					break;
				case 1:
					if (!(length >= 1)) break;
					length = 1;
					ruleOffset = character->otherRules;
					break;
				case 2: /* No rule found */
					currentOpcode = CTO_Always;
					ruleOffset = 0;
					break;
				}
				while (ruleOffset) {
					currentRule = (TranslationTableRule *)&table->ruleArea[ruleOffset];
					currentOpcode = currentRule->opcode;
					int currentCharslen = currentRule->charslen;
					if (tryThis == 1 ||
							(currentCharslen <= length &&
									compareChars(&currentRule->charsdots[0],
											&input->chars[pos], currentCharslen,
											table))) {
						if (currentOpcode == CTO_Correct &&
								back_passDoTest(table, pos, input, currentOpcode,
										currentRule, &passInstructions, &passIC,
										&patternMatch)) {
							tryThis = 4;
							break;
						}
					}
					ruleOffset = currentRule->dotsnext;
				}
				tryThis++;
			}
		switch (currentOpcode) {
		case CTO_Always:
			if (output->length >= output->maxlength) goto failure;
			posMapping[pos] = output->length;
			output->chars[(output->length)++] = input->chars[pos++];
			break;
		case CTO_Correct:
			if (appliedRules != NULL && *appliedRulesCount < maxAppliedRules)
				appliedRules[(*appliedRulesCount)++] = currentRule;
			if (!back_passDoAction(table, &pos, mode, input, output, posMapping,
						cursorPosition, cursorStatus, &nextUpper, allUpper,
						allUpperPhrase, currentOpcode, currentRule, passInstructions,
						passIC, patternMatch))
				goto failure;
			break;
		default:
			break;
		}
		posIncremented = pos > posBefore;
	}
failure:
	*realInlen = pos;
	return 1;
}

static int
backTranslateString(const TranslationTableHeader *table, int mode, int currentPass,
		const InString *input, OutString *output, char *spacebuf, int *posMapping,
		int *realInlen, int *cursorPosition, int *cursorStatus,
		const TranslationTableRule **appliedRules, int *appliedRulesCount,
		int maxAppliedRules) {
	int pos;
	int nextUpper;
	int allUpper;
	int allUpperPhrase;
	int itsANumber;
	int itsALetter;
	/* Back translation */
	int srcword = 0;
	int destword = 0; /* last word translated */
	TranslationTableOpcode previousOpcode;
	int doingMultind = 0;
	const TranslationTableRule *multindRule;
	_lou_resetPassVariables();
	translation_direction = 0;
	nextUpper = allUpper = allUpperPhrase = itsANumber = itsALetter = 0;
	previousOpcode = CTO_None;
	pos = output->length = 0;
	while (pos < input->length) {
		/* the main translation loop */
		int currentDotslen; /* length of current find string */
		TranslationTableOpcode currentOpcode;
		const TranslationTableRule *currentRule; /* pointer to current rule in table */
		TranslationTableCharacterAttributes beforeAttributes;
		const widechar *passInstructions;
		int passIC; /* Instruction counter */
		PassRuleMatch patternMatch;
		back_setBefore(table, output, &beforeAttributes);
		if ((allUpper == 1) && (beforeAttributes & CTC_UpperCase))
			// Capsword in progress
			allUpper = 2;
		else if ((allUpper == 2) && !(beforeAttributes & CTC_UpperCase) &&
				!(beforeAttributes & CTC_CapsMode))
			// terminate capsword
			allUpper = 0;
		if ((itsANumber == 2) && output->length > 0 &&
				!(beforeAttributes & CTC_LitDigit) &&
				!(beforeAttributes & CTC_NumericMode) &&
				!(beforeAttributes & CTC_MidEndNumericMode))
			itsANumber = 0;
		back_selectRule(table, pos, mode, input, output, itsANumber, itsALetter,
				&currentDotslen, &currentOpcode, &currentRule, previousOpcode,
				&doingMultind, &multindRule, beforeAttributes, &passInstructions, &passIC,
				&patternMatch);
		if (appliedRules != NULL && *appliedRulesCount < maxAppliedRules)
			appliedRules[(*appliedRulesCount)++] = currentRule;
		/* processing before replacement */
		switch (currentOpcode) {
		case CTO_LargeSign:
			if (previousOpcode == CTO_LargeSign)
				if (!insertSpace(table, pos, input, output, spacebuf, posMapping,
							cursorPosition, cursorStatus, &nextUpper, allUpper,
							allUpperPhrase))
					goto failure;
			break;
		case CTO_CapsLetterRule:
			nextUpper = 1;
			allUpper = 0;
			itsANumber = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_BegCapsWordRule:
			allUpper = 1;
			itsANumber = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_BegCapsRule:
			allUpperPhrase = 1;
			itsANumber = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_EndCapsWordRule:
			allUpper = 0;
			itsANumber = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_EndCapsRule:
			allUpperPhrase = 0;
			itsANumber = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_LetterRule:
		case CTO_NoContractRule:
			itsALetter = 1;
			itsANumber = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_NumberRule:
			itsANumber = 1;  // Starting number
			allUpper = 0;
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;
		case CTO_LitDigit:
			itsANumber = 2;  // In the middle of a number
			break;
		case CTO_BegCompRule:
			itsANumber = 0;
		case CTO_BegEmph1Rule:
		case CTO_BegEmph2Rule:
		case CTO_BegEmph3Rule:
		case CTO_EndEmph1Rule:
		case CTO_EndEmph2Rule:
		case CTO_EndEmph3Rule:
		case CTO_EndCompRule:
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			continue;
			break;

		default:
			break;
		}

		/* replacement processing */
		switch (currentOpcode) {
		case CTO_Context:
			if (!back_passDoAction(table, &pos, mode, input, output, posMapping,
						cursorPosition, cursorStatus, &nextUpper, allUpper,
						allUpperPhrase, currentOpcode, currentRule, passInstructions,
						passIC, patternMatch))
				return 0;
			break;
		case CTO_Replace:
			while (currentDotslen-- > 0) posMapping[pos++] = output->length;
			if (!putCharacters(&currentRule->charsdots[0], currentRule->charslen, table,
						pos, mode, input, output, posMapping, cursorPosition,
						cursorStatus, &nextUpper, allUpper, allUpperPhrase))
				goto failure;
			break;
		case CTO_None:
			if (!undefinedDots(input->chars[pos], mode, output, pos, posMapping))
				goto failure;
			pos++;
			break;
		case CTO_BegNum:
			itsANumber = 1;
			goto insertChars;
		case CTO_EndNum:
			itsANumber = 0;
			goto insertChars;
		case CTO_Space:
			itsALetter = itsANumber = allUpper = nextUpper = 0;
			goto insertChars;
		default:
		insertChars:
			if (currentRule->charslen) {
				if (!back_updatePositions(&currentRule->charsdots[0],
							currentRule->dotslen, currentRule->charslen, table, pos,
							input, output, posMapping, cursorPosition, cursorStatus,
							&nextUpper, allUpper, allUpperPhrase))
					goto failure;
				pos += currentDotslen;
			} else {
				int srclim = pos + currentDotslen;
				while (1) {
					if (!putCharacter(input->chars[pos], table, pos, mode, input, output,
								posMapping, cursorPosition, cursorStatus, &nextUpper,
								allUpper, allUpperPhrase))
						goto failure;
					if (++pos == srclim) break;
				}
			}
		}

		/* processing after replacement */
		switch (currentOpcode) {
		case CTO_JoinNum:
		case CTO_JoinableWord:
			if (!insertSpace(table, pos, input, output, spacebuf, posMapping,
						cursorPosition, cursorStatus, &nextUpper, allUpper,
						allUpperPhrase))
				goto failure;
			break;
		default:
			passSelectRule(table, pos, currentPass, input, &currentOpcode, &currentRule,
					&passInstructions, &passIC, &patternMatch);
			if (currentOpcode == CTO_Context) {
				back_passDoAction(table, &pos, mode, input, output, posMapping,
						cursorPosition, cursorStatus, &nextUpper, allUpper,
						allUpperPhrase, currentOpcode, currentRule, passInstructions,
						passIC, patternMatch);
			}
			break;
		}
		if (((pos > 0) && checkDotsAttr(input->chars[pos - 1], CTC_Space, table) &&
					(currentOpcode != CTO_JoinableWord))) {
			srcword = pos;
			destword = output->length;
		}
		if ((currentOpcode >= CTO_Always && currentOpcode <= CTO_None) ||
				(currentOpcode >= CTO_Digit && currentOpcode <= CTO_LitDigit))
			previousOpcode = currentOpcode;
	} /* end of translation loop */
failure:

	if (destword != 0 && pos < input->length &&
			!checkDotsAttr(input->chars[pos], CTC_Space, table)) {
		pos = srcword;
		output->length = destword;
	}
	if (pos < input->length) {
		while (checkDotsAttr(input->chars[pos], CTC_Space, table))
			if (++pos == input->length) break;
	}
	*realInlen = pos;
	return 1;
} /* translation completed */

/* Multipass translation */

static int
matchCurrentInput(
		const InString *input, int pos, const widechar *passInstructions, int passIC) {
	int k;
	int kk = pos;
	for (k = passIC + 2; k < passIC + 2 + passInstructions[passIC + 1]; k++)
		if (passInstructions[k] != input->chars[kk++]) return 0;
	return 1;
}

static int
back_swapTest(const TranslationTableHeader *table, const InString *input, int *pos,
		const widechar *passInstructions, int passIC) {
	int curLen;
	int curTest;
	int curSrc = *pos;
	TranslationTableOffset swapRuleOffset;
	TranslationTableRule *swapRule;
	swapRuleOffset = (passInstructions[passIC + 1] << 16) | passInstructions[passIC + 2];
	swapRule = (TranslationTableRule *)&table->ruleArea[swapRuleOffset];
	for (curLen = 0; curLen < passInstructions[passIC] + 3; curLen++) {
		for (curTest = 0; curTest < swapRule->charslen; curTest++) {
			if (input->chars[curSrc] == swapRule->charsdots[curTest]) break;
		}
		if (curTest == swapRule->charslen) return 0;
		curSrc++;
	}
	if (passInstructions[passIC + 2] == passInstructions[passIC + 3]) {
		*pos = curSrc;
		return 1;
	}
	while (curLen < passInstructions[passIC + 4]) {
		for (curTest = 0; curTest < swapRule->charslen; curTest++) {
			if (input->chars[curSrc] != swapRule->charsdots[curTest]) break;
		}
		if (curTest < swapRule->charslen)
			if (curTest < swapRule->charslen) {
				*pos = curSrc;
				return 1;
			}
		curSrc++;
		curLen++;
	}
	*pos = curSrc;
	return 1;
}

static int
back_swapReplace(int start, int end, const TranslationTableHeader *table,
		const InString *input, OutString *output, int *posMapping,
		const widechar *passInstructions, int passIC) {
	TranslationTableOffset swapRuleOffset;
	TranslationTableRule *swapRule;
	widechar *replacements;
	int p;
	int lastPos = 0;
	int lastRep = 0;
	swapRuleOffset = (passInstructions[passIC + 1] << 16) | passInstructions[passIC + 2];
	swapRule = (TranslationTableRule *)&table->ruleArea[swapRuleOffset];
	replacements = &swapRule->charsdots[swapRule->charslen];
	for (p = start; p < end; p++) {
		int rep;
		int test;
		int k;
		for (test = 0; test < swapRule->charslen; test++)
			if (input->chars[p] == swapRule->charsdots[test]) break;
		if (test == swapRule->charslen) return p;
		if (test >= lastRep) {
			k = lastPos;
			rep = lastRep;
		} else {
			k = 0;
			rep = 0;
		}
		while (k < swapRule->dotslen) {
			if (rep == test) {
				int l = replacements[k] - 1;
				if (output->length + l >= output->maxlength) return 0;
				posMapping[p] = output->length;
				memcpy(&output->chars[output->length], &replacements[k + 1],
						l * CHARSIZE);
				output->length += l;
				lastPos = k;
				lastRep = rep;
				break;
			}
			rep++;
			k += replacements[k];
		}
	}
	return p;
}

static int
back_passDoTest(const TranslationTableHeader *table, int pos, const InString *input,
		TranslationTableOpcode currentOpcode, const TranslationTableRule *currentRule,
		const widechar **passInstructions, int *passIC, PassRuleMatch *match) {
	int k;
	int m;
	int not = 0;
	TranslationTableCharacterAttributes attributes;
	*passInstructions = &currentRule->charsdots[currentRule->charslen];
	*passIC = 0;
	match->startMatch = match->endMatch = pos;
	match->startReplace = -1;
	if (currentOpcode == CTO_Correct)
		m = 0;
	else
		m = 1;
	while (*passIC < currentRule->dotslen) {
		int itsTrue = 1;
		if (pos > input->length) return 0;
		switch ((*passInstructions)[*passIC]) {
		case pass_first:
			if (pos != 0) itsTrue = 0;
			(*passIC)++;
			break;
		case pass_last:
			if (pos != input->length) itsTrue = 0;
			(*passIC)++;
			break;
		case pass_lookback:
			pos -= (*passInstructions)[*passIC + 1];
			if (pos < 0) {
				pos = 0;
				itsTrue = 0;
			}
			*passIC += 2;
			break;
		case pass_not:
			not = !not;
			(*passIC)++;
			continue;
		case pass_string:
		case pass_dots:
			itsTrue = matchCurrentInput(input, pos, *passInstructions, *passIC);
			pos += (*passInstructions)[*passIC + 1];
			*passIC += (*passInstructions)[*passIC + 1] + 2;
			break;
		case pass_startReplace:
			match->startReplace = pos;
			(*passIC)++;
			break;
		case pass_endReplace:
			match->endReplace = pos;
			(*passIC)++;
			break;
		case pass_attributes:
			attributes = (*passInstructions)[*passIC + 1];
			attributes <<= 16;
			attributes |= (*passInstructions)[*passIC + 2];
			attributes <<= 16;
			attributes |= (*passInstructions)[*passIC + 3];
			attributes <<= 16;
			attributes |= (*passInstructions)[*passIC + 4];
			for (k = 0; k < (*passInstructions)[*passIC + 5]; k++) {
				if (pos >= input->length) {
					itsTrue = 0;
					break;
				}
				if (!((m ? getDots(input->chars[pos], table)
						 : getChar(input->chars[pos], table))
									->attributes &
							attributes)) {
					itsTrue = 0;
					break;
				}
				pos++;
			}
			if (itsTrue) {
				for (k = (*passInstructions)[*passIC + 5];
						k < (*passInstructions)[*passIC + 6] && pos < input->length;
						k++) {
					if (!((m ? getDots(input->chars[pos], table)
							 : getChar(input->chars[pos], table))
										->attributes &
								attributes))
						break;
					pos++;
				}
			}
			*passIC += 7;
			break;
		case pass_swap:
			itsTrue = back_swapTest(table, input, &pos, *passInstructions, *passIC);
			*passIC += 5;
			break;
		case pass_endTest: {
			(*passIC)++;
			match->endMatch = pos;
			if (match->startReplace == -1) {
				match->startReplace = match->startMatch;
				match->endReplace = match->endMatch;
			}
			return 1;
			break;
		}
		default:
			if (_lou_handlePassVariableTest(*passInstructions, passIC, &itsTrue)) break;
			return 0;
		}
		if ((!not&&!itsTrue) || (not&&itsTrue)) return 0;
		not = 0;
	}
	return 1;
}

static int
copyCharacters(int from, int to, const TranslationTableHeader *table, int mode,
		const InString *input, OutString *output, int *posMapping, int *cursorPosition,
		int *cursorStatus, int *nextUpper, int allUpper, int allUpperPhrase,
		TranslationTableOpcode currentOpcode) {
	if (currentOpcode == CTO_Context) {
		while (from < to) {
			if (!putCharacter(input->chars[from], table, from, mode, input, output,
						posMapping, cursorPosition, cursorStatus, nextUpper, allUpper,
						allUpperPhrase))
				return 0;
			from++;
		}
	} else {
		if (to > from) {
			if ((output->length + to - from) > output->maxlength) return 0;
			while (to > from) {
				posMapping[from] = output->length;
				output->chars[output->length] = input->chars[from];
				output->length++;
				from++;
			}
		}
	}

	return 1;
}

static int
back_passDoAction(const TranslationTableHeader *table, int *pos, int mode,
		const InString *input, OutString *output, int *posMapping, int *cursorPosition,
		int *cursorStatus, int *nextUpper, int allUpper, int allUpperPhrase,
		TranslationTableOpcode currentOpcode, const TranslationTableRule *currentRule,
		const widechar *passInstructions, int passIC, PassRuleMatch match) {
	int k;
	int destStartMatch = output->length;
	int destStartReplace;
	int newPos = match.endReplace;

	if (!copyCharacters(match.startMatch, match.startReplace, table, mode, input, output,
				posMapping, cursorPosition, cursorStatus, nextUpper, allUpper,
				allUpperPhrase, currentOpcode))
		return 0;
	destStartReplace = output->length;

	for (k = match.startReplace; k < match.endReplace; k++)
		posMapping[k] = output->length;
	while (passIC < currentRule->dotslen) switch (passInstructions[passIC]) {
		case pass_string:
		case pass_dots:
			if ((output->length + passInstructions[passIC + 1]) > output->maxlength)
				return 0;
			memcpy(&output->chars[output->length], &passInstructions[passIC + 2],
					passInstructions[passIC + 1] * sizeof(*output->chars));
			output->length += passInstructions[passIC + 1];
			passIC += passInstructions[passIC + 1] + 2;
			break;
		case pass_swap:
			if (!back_swapReplace(match.startReplace, match.endReplace, table, input,
						output, posMapping, passInstructions, passIC))
				return 0;
			passIC += 3;
			break;
		case pass_omit:
			passIC++;
			break;
		case pass_copy: {
			int count = destStartReplace - destStartMatch;
			if (count > 0) {
				memmove(&output->chars[destStartMatch], &output->chars[destStartReplace],
						count * sizeof(*output->chars));
				output->length -= count;
				destStartReplace = destStartMatch;
			}
		}

			if (!copyCharacters(match.startReplace, match.endReplace, table, mode, input,
						output, posMapping, cursorPosition, cursorStatus, nextUpper,
						allUpper, allUpperPhrase, currentOpcode))
				return 0;
			newPos = match.endMatch;
			passIC++;
			break;
		default:
			if (_lou_handlePassVariableAction(passInstructions, &passIC)) break;
			return 0;
		}
	*pos = newPos;
	return 1;
}

static void
passSelectRule(const TranslationTableHeader *table, int pos, int currentPass,
		const InString *input, TranslationTableOpcode *currentOpcode,
		const TranslationTableRule **currentRule, const widechar **passInstructions,
		int *passIC, PassRuleMatch *match) {
	if (!findBackPassRule(table, pos, currentPass, input, currentOpcode, currentRule,
				passInstructions, passIC, match)) {
		*currentOpcode = CTO_Always;
	}
}

static int
translatePass(const TranslationTableHeader *table, int mode, int currentPass,
		const InString *input, OutString *output, int *posMapping, int *realInlen,
		int *cursorPosition, int *cursorStatus, const TranslationTableRule **appliedRules,
		int *appliedRulesCount, int maxAppliedRules) {
	int pos;
	int posIncremented = 1;
	int nextUpper = 0;
	int allUpper = 0;
	int allUpperPhrase = 0;
	pos = output->length = 0;
	_lou_resetPassVariables();
	while (pos < input->length) { /* the main multipass translation loop */
		int posBefore = pos;
		TranslationTableOpcode currentOpcode;
		const TranslationTableRule *currentRule; /* pointer to current rule in table */
		const widechar *passInstructions;
		int passIC; /* Instruction counter */
		PassRuleMatch patternMatch;
		if (!posIncremented)
			currentOpcode = CTO_Always;
		else
			passSelectRule(table, pos, currentPass, input, &currentOpcode, &currentRule,
					&passInstructions, &passIC, &patternMatch);
		switch (currentOpcode) {
		case CTO_Pass2:
		case CTO_Pass3:
		case CTO_Pass4:
			if (appliedRules != NULL && *appliedRulesCount < maxAppliedRules)
				appliedRules[(*appliedRulesCount)++] = currentRule;
			if (!back_passDoAction(table, &pos, mode, input, output, posMapping,
						cursorPosition, cursorStatus, &nextUpper, allUpper,
						allUpperPhrase, currentOpcode, currentRule, passInstructions,
						passIC, patternMatch))
				goto failure;
			break;
		case CTO_Always:
			if ((output->length + 1) > output->maxlength) goto failure;
			posMapping[pos] = output->length;
			output->chars[(output->length)++] = input->chars[pos++];
			break;
		default:
			goto failure;
		}
		posIncremented = pos > posBefore;
	}
failure:
	if (pos < input->length) {
		while (checkDotsAttr(input->chars[pos], CTC_Space, table))
			if (++pos == input->length) break;
	}
	*realInlen = pos;
	return 1;
}
