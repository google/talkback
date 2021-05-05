# `ScreenCaptureController`

The `ScreenCaptureController` API allows client applications to request
permission for and take screenshots of any Android screen. There are two main
methods used to accomplish this:

 * `authorizeCaptureAsync(AuthorizationListener)` requests permission of the
 user to capture their screen.
 * `requestScreenCaptureAsync(CaptureListener)` takes a screenshot.

## API
`authorizeCaptureAsync()` will request the necessary screen capture permissions
that are used to fetch an image of the screen. This method should ideally only
need to be called once, as long as you use the same instance of
`ScreenCaptureController` to call `requestScreenCaptureAsync()` as you use to
call `authorizeCaptureAsync()`.  Calling `deauthorizeCapture()` will
end the underlying `MediaProjection` session.  Note that users have the ability
to manually end a seesion at any time.  While aurhotized, a notification icon
will appear within the device's status bar, informing them of the application's
ability to capture screenshots.

`requestScreenCaptureAsync()` will take a screenshot, and then will call back
`CaptureListener.onScreenCaptureFinished()`. If the instance of
`ScreenCaptureController` has not been authorized, authorization will be
requested and retained for just this single screenshot, after which the instance
of `ScreenCaptureController` with self-deauthorize.

`shutdown()` disposes of underlying resources, and should be invoked when an
instance of `ScreenCaptureController` is no longer needed.

## Compatibility

The minimum supported API version for this library is 22. Note that docs for
`MediaProjection` APIs say 21, but are incorrect.  If you attempt to use public
methods exposed by this library on devices with API versions earlier than 22,
they will no-op, and provided callbacks will not be invoked.

An implementing application may choose to define an `android:taskAffinity`
attribute in its local `AndroidManifest.xml` declaration of
`ScreenshotAuthProxyActivity` to ensure it is not added to the same task group
as the application's other activities.
