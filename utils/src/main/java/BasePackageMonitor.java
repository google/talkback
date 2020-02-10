package com.google.android.accessibility.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Helper class for monitoring packages on the system.
 */
public abstract class BasePackageMonitor extends BroadcastReceiver {

  /** The intent filter to match package modifications. */
  private final IntentFilter packageFilter;

  /** The context in which this monitor is registered. */
  @Nullable private Context registeredContext;

  /** Creates a new instance. */
  public BasePackageMonitor() {
    packageFilter = new IntentFilter();
    packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
    packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
    packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
    packageFilter.addDataScheme("package");
  }

  /**
   * Register this monitor via the given {@link Context}. Throws an {@link IllegalStateException} if
   * this monitor was already registered.
   */
  public void register(Context context) {
    if (registeredContext != null) {
      throw new IllegalStateException("Already registered");
    }

    registeredContext = context;
    context.registerReceiver(this, packageFilter);
  }

  /**
   * Unregister this monitor. Throws an {@link IllegalStateException} if this monitor wasn't
   * registered.
   */
  public void unregister() {
    if (registeredContext == null) {
      throw new IllegalStateException("Not registered");
    }

    registeredContext.unregisterReceiver(this);
    registeredContext = null;
  }

  @Override
  public void onReceive(Context context, Intent intent) {
    final String packageName = getPackageName(intent);
    final String action = intent.getAction();

    if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
      onPackageAdded(packageName);
    } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
      onPackageRemoved(packageName);
    } else if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
      onPackageChanged(packageName);
    }
  }

  /** @return The name of the package from an {@link Intent} */
  private static @Nullable String getPackageName(Intent intent) {
    final Uri uri = intent.getData();

    if (uri == null) {
      return null;
    }

    return uri.getSchemeSpecificPart();
  }

  /**
   * Called when a new application package has been installed on the device.
   *
   * @param packageName The name of the package that was added
   */
  protected abstract void onPackageAdded(String packageName);

  /**
   * Called when an existing application package has been removed from the device.
   *
   * @param packageName The name of the package that was removed
   */
  protected abstract void onPackageRemoved(String packageName);

  /**
   * Called when an existing application package has been changed (e.g. a component has been
   * disabled or enabled).
   *
   * @param packageName The name of the package that was changed
   */
  protected abstract void onPackageChanged(String packageName);
}
