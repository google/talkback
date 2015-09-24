/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.utils;

import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.Engine;
import android.speech.tts.TextToSpeech.EngineInfo;
import android.text.TextUtils;
import com.android.utils.compat.provider.SettingsCompatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Support class for querying the list of available engines on the device and
 * deciding which one to use etc.
 * <p>
 * Comments in this class the use the shorthand "system engines" for engines
 * that are a part of the system image.
 * <p>
 * Based on hidden framework class {@code android.speech.tts.TtsEngines}.
 */
@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1)
public class TtsEngineUtils {
    /** The default delimiter for {@link Locale} strings. */
    private static final String LOCALE_DELIMITER = "-";

    private TtsEngineUtils() {
        // This class is not publicly instantiable.
    }

    /**
     * Returns the package name of the default TTS engine. If the user has set a
     * default, and the engine is available on the device, the default is
     * returned. Otherwise, the highest ranked engine is returned. If no system
     * engine is present, returns {@code null}.
     *
     * @return the package name of the default TTS engine, or {@code null} if
     *         no default or system engine is present.
     */
    public static String getDefaultEngine(Context context) {
        final ContentResolver resolver = context.getContentResolver();
        final String defaultEngine = Settings.Secure.getString(
                resolver, Settings.Secure.TTS_DEFAULT_SYNTH);
        if (isEngineInstalled(context, defaultEngine)) {
            return defaultEngine;
        }

        // Fall back on the highest-ranked system engine.
        final TtsEngineInfo engine = getHighestRankedEngine(context);
        if (engine != null) {
            return engine.name;
        }

        return null;
    }

    /**
     * Returns the default locale for a given TTS engine. Attempts to read the
     * value from {@link SettingsCompatUtils.SecureCompatUtils#TTS_DEFAULT_LOCALE}, failing which
     * the old style value from {@link Settings.Secure#TTS_DEFAULT_LANG} is
     * read. If both these values are empty, the default phone locale is
     * returned.
     *
     * @param engineName the engine to return the locale for.
     * @return the locale string preference for this engine. Will be non null
     *         and non empty.
     */
    @SuppressWarnings("javadoc")
    public static Locale getDefaultLocaleForEngine(Context context, String engineName) {
        final ContentResolver resolver = context.getContentResolver();
        final String defaultLocalePref = Settings.Secure.getString(
                resolver, SettingsCompatUtils.SecureCompatUtils.TTS_DEFAULT_LOCALE);
        final Locale locale = parseEngineLocalePrefFromList(defaultLocalePref, engineName);
        if (locale != null) {
            return locale;
        }

        // If the new-style setting is not set, return the old-style setting.
        return getV1Locale(resolver);
    }

    /**
     * Gets a list of all installed TTS engines sorted by priority (see
     * {@link #ENGINE_PRIORITY_COMPARATOR}).
     *
     * @return A sorted list of engine info objects. The list can be empty, but
     *         never {@code null}.
     */
    private static List<TtsEngineInfo> getEngines(Context context) {
        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(Engine.INTENT_ACTION_TTS_SERVICE);
        final List<ResolveInfo> resolveInfos = pm.queryIntentServices(
                intent, PackageManager.MATCH_DEFAULT_ONLY);
        final List<TtsEngineInfo> engines = new ArrayList<>(resolveInfos.size());

        for (ResolveInfo resolveInfo : resolveInfos) {
            final TtsEngineInfo engine = getEngineInfo(resolveInfo, pm);
            if (engine != null) {
                engines.add(engine);
            }
        }

        Collections.sort(engines, ENGINE_PRIORITY_COMPARATOR);

        return Collections.unmodifiableList(engines);
    }

    /**
     * Parses the contents of {@link Engine#EXTRA_AVAILABLE_VOICES} and returns
     * a unmodifiable list of {@link Locale}s sorted by display name. See
     * {@link #LOCALE_COMPARATOR} for sorting information.
     *
     * @param availableLanguages A list of locale strings in the form
     *            {@code language-country-variant}.
     * @return A sorted, unmodifiable list of {@link Locale}s.
     */
    public static List<Locale> parseAvailableLanguages(List<String> availableLanguages) {
        final List<Locale> results = new ArrayList<>(availableLanguages.size());

        for (String availableLang : availableLanguages) {
            final String[] langCountryVar = availableLang.split("-");
            final Locale loc;

            if (langCountryVar.length == 1) {
                loc = new Locale(langCountryVar[0]);
            } else if (langCountryVar.length == 2) {
                loc = new Locale(langCountryVar[0], langCountryVar[1]);
            } else if (langCountryVar.length == 3) {
                loc = new Locale(langCountryVar[0], langCountryVar[1], langCountryVar[2]);
            } else {
                continue;
            }

            results.add(loc);
        }

        // Sort by display name, ascending case-insensitive.
        Collections.sort(results, LOCALE_COMPARATOR);

        return Collections.unmodifiableList(results);
    }

    /**
     * Returns the engine info for a given engine name. Note that engines are
     * identified by their package name.
     */
    private static TtsEngineInfo getEngineInfo(Context context, String packageName) {
        if (packageName == null) {
            return null;
        }

        final PackageManager pm = context.getPackageManager();
        final Intent intent = new Intent(Engine.INTENT_ACTION_TTS_SERVICE).setPackage(packageName);
        final List<ResolveInfo> resolveInfos = pm.queryIntentServices(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        if ((resolveInfos == null) || resolveInfos.isEmpty()) {
            return null;
        }

        // Note that the current API allows only one engine per
        // package name. Since the "engine name" is the same as
        // the package name.
        return getEngineInfo(resolveInfos.get(0), pm);
    }

    /**
     * @return true if a given engine is installed on the system.
     */
    private static boolean isEngineInstalled(Context context, String engine) {
        return getEngineInfo(context, engine) != null;
    }

    /**
     * @return information for the highest ranked system engine or {@code null}
     *         if no TTS engines were present in the system image.
     */
    private static TtsEngineInfo getHighestRankedEngine(Context context) {
        final List<TtsEngineInfo> sortedEngines = getEngines(context);
        for (TtsEngineInfo engine : sortedEngines) {
            if (engine.system) {
                return engine;
            }
        }

        return null;
    }

    private static TtsEngineInfo getEngineInfo(ResolveInfo resolve, PackageManager pm) {
        final ServiceInfo service = resolve.serviceInfo;
        if (service == null) {
            return null;
        }

        final TtsEngineInfo engine = new TtsEngineInfo();

        // Using just the package name isn't great, since it disallows having
        // multiple engines in the same package, but that's what the existing
        // API does.
        engine.name = service.packageName;

        final CharSequence label = service.loadLabel(pm);
        engine.label = TextUtils.isEmpty(label) ? engine.name : label.toString();
        engine.icon = service.getIconResource();
        engine.priority = resolve.priority;
        engine.system = isSystemEngine(service);

        return engine;
    }

    private static boolean isSystemEngine(ServiceInfo info) {
        final ApplicationInfo appInfo = info.applicationInfo;
        return appInfo != null && ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

    }

    /**
     * @return the old style locale string constructed from
     *         {@link Settings.Secure#TTS_DEFAULT_LANG},
     *         {@link Settings.Secure#TTS_DEFAULT_COUNTRY} and
     *         {@link Settings.Secure#TTS_DEFAULT_VARIANT}. If no such locale is
     *         set, then return the default phone locale.
     */
    @SuppressWarnings({"deprecation", "javadoc"})
    private static Locale getV1Locale(ContentResolver resolver) {
        final String language = Settings.Secure.getString(
                resolver, Settings.Secure.TTS_DEFAULT_LANG);
        if (TextUtils.isEmpty(language)) {
            return Locale.getDefault();
        }

        final String country = Settings.Secure.getString(
                resolver, Settings.Secure.TTS_DEFAULT_COUNTRY);
        final String variant = Settings.Secure.getString(
                resolver, Settings.Secure.TTS_DEFAULT_VARIANT);
        final String locale = constructLocaleString(language, country, variant);

        return new Locale(locale);
    }

    private static String constructLocaleString(String language, String country, String variant) {
        final StringBuilder builder = new StringBuilder(language);
        if (TextUtils.isEmpty(language)) {
            return builder.toString();
        }

        builder.append(language);

        if (TextUtils.isEmpty(country)) {
            return builder.toString();
        }

        builder.append(LOCALE_DELIMITER);
        builder.append(country);

        if (TextUtils.isEmpty(variant)) {
            return builder.toString();
        }

        builder.append(LOCALE_DELIMITER);
        builder.append(variant);

        return builder.toString();
    }

    /**
     * Parses a comma separated list of engine locale preferences. The list is
     * of the form {@code "engine_name_1:locale_1,engine_name_2:locale2"} and so
     * on and so forth. Returns null if the list is empty, malformed or if there
     * is no engine specific preference in the list.
     */
    private static Locale parseEngineLocalePrefFromList(String prefValue, String engineName) {
        if (TextUtils.isEmpty(prefValue)) {
            return null;
        }

        final String[] prefValues = prefValue.split(",");
        for (String value : prefValues) {
            final int delimiter = value.indexOf(':');
            if (delimiter > 0) {
                if (engineName.equals(value.substring(0, delimiter))) {
                    return new Locale(value.substring(delimiter + 1));
                }
            }
        }

        return null;
    }

    /**
     * Compares locales in case-insensitive ascending order based on their
     * display name.
     */
    private static final Comparator<Locale> LOCALE_COMPARATOR = new Comparator<Locale>() {
        @Override
        public int compare(Locale lhs, Locale rhs) {
            return lhs.getDisplayName().compareToIgnoreCase(rhs.getDisplayName());
        }
    };

    /**
     * Engines that are a part of the system image are always lesser than those
     * that are not. Within system engines / non system engines the engines are
     * sorted in order of their declared priority.
     */
    private static final Comparator<TtsEngineInfo>
            ENGINE_PRIORITY_COMPARATOR = new Comparator<TtsEngineInfo>() {
                @Override
                public int compare(TtsEngineInfo lhs, TtsEngineInfo rhs) {
                    if (lhs.system && !rhs.system) {
                        return -1;
                    } else if (rhs.system && !lhs.system) {
                        return 1;
                    } else {
                        // Either both system engines, or both non system
                        // engines. Note:
                        // this isn't a typo. Higher priority numbers imply
                        // higher
                        // priority, but are "lower" in the sort order.
                        return (rhs.priority - lhs.priority);
                    }
                }
            };

    /**
     * Information about an installed text-to-speech engine. Cloned from
     * {@link android.speech.tts.TextToSpeech.EngineInfo}.
     *
     * @see TextToSpeech#getEngines
     * @see EngineInfo
     */
    public static class TtsEngineInfo {
        /** Engine package name. */
        public String name;

        /** Localized label for the engine. */
        public String label;

        /** Icon for the engine. */
        public int icon;

        /** Whether this engine is a part of the system image. */
        public boolean system;

        /**
         * The priority the engine declares for the intent filter
         * {@code android.intent.action.TTS_SERVICE}.
         */
        public int priority;

        @Override
        public String toString() {
            return "TtsEngineInfo{name=" + name + "}";
        }
    }
}
