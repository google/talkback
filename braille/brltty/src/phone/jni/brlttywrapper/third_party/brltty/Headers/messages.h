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

#ifndef BRLTTY_INCLUDED_MESSAGES
#define BRLTTY_INCLUDED_MESSAGES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int setMessagesDirectory (const char *directory);
extern const char *getMessagesDirectory (void);

extern int setMessagesLocale (const char *specifier);
extern const char *getMessagesLocale (void);

extern int setMessagesDomain (const char *name);
extern const char *getMessagesDomain (void);

extern void ensureAllMessagesProperties (void);

extern int loadMessageCatalog (void);
extern void releaseMessageCatalog (void);

extern uint32_t getMessageCount (void);
extern const char *getMessagesMetadata (void);
extern char *getMessagesProperty (const char *name);
extern char *getMessagesAttribute (const char *property, const char *name);

#define MESSAGES_PROPERTY_MIME_VERSION "MIME-Version"
#define MESSAGES_PROPERTY_CONTENT_TYPE "Content-Type"
#define MESSAGES_PROPERTY_CONTENT_TRANSFER_ENCODING "Content-Transfer-Encoding"

#define MESSAGES_PROPERTY_PROJECT_VERSION "Project-Id-Version"
#define MESSAGES_PROPERTY_LANGUAGE_TEAM "Language-Team"
#define MESSAGES_PROPERTY_MSGID_BUGS "Report-Msgid-Bugs-To"

#define MESSAGES_PROPERTY_LANGUAGE_CODE "Language"
#define MESSAGES_PROPERTY_PLURAL_FORMS "Plural-Forms"

#define MESSAGES_PROPERTY_LAST_TRANSLATOR "Last-Translator"
#define MESSAGES_PROPERTY_REVISION_DATE "PO-Revision-Date"

typedef struct MessageStruct Message;
extern const char *getMessageText (const Message *message);
extern uint32_t getMessageLength (const Message *message);

extern const Message *getSourceMessage (unsigned int index);
extern const Message *getTranslatedMessage (unsigned int index);

extern int findSourceMessage (const char *text, size_t textLength, unsigned int *index);
extern const Message *findSimpleTranslation (const char *text, size_t length);
extern const Message *findPluralTranslation (const char *const *strings);

extern const char *getSimpleTranslation (const char *text);
extern const char *getPluralTranslation (const char *singular, const char *plural, unsigned long int count);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_MESSAGES */
