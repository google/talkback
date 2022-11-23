# Dependencies for activity.$O:
activity.$O: $(SRC_DIR)/activity.c
activity.$O: $(SRC_TOP)Headers/prologue.h
activity.$O: $(BLD_TOP)config.h
activity.$O: $(BLD_TOP)forbuild.h
activity.$O: $(SRC_TOP)Headers/log.h
activity.$O: $(SRC_DIR)/parameters.h
activity.$O: $(SRC_DIR)/activity.h
activity.$O: $(SRC_TOP)Headers/async.h
activity.$O: $(SRC_TOP)Headers/async_alarm.h
activity.$O: $(SRC_TOP)Headers/timing.h
activity.$O: $(SRC_TOP)Headers/async_wait.h

# Dependencies for addresses.$O:
addresses.$O: $(SRC_DIR)/addresses.c
addresses.$O: $(SRC_TOP)Headers/prologue.h
addresses.$O: $(BLD_TOP)config.h
addresses.$O: $(BLD_TOP)forbuild.h
addresses.$O: $(SRC_TOP)Headers/addresses.h
addresses.$O: $(SRC_TOP)Headers/log.h
addresses.$O: $(SRC_TOP)Headers/dynld.h
addresses.$O: $(SRC_TOP)Headers/pid.h
addresses.$O: $(SRC_TOP)Headers/program.h

# Dependencies for alert.$O:
alert.$O: $(SRC_DIR)/alert.c
alert.$O: $(SRC_TOP)Headers/prologue.h
alert.$O: $(BLD_TOP)config.h
alert.$O: $(BLD_TOP)forbuild.h
alert.$O: $(SRC_TOP)Headers/alert.h
alert.$O: $(SRC_TOP)Headers/pid.h
alert.$O: $(SRC_TOP)Headers/program.h
alert.$O: $(SRC_TOP)Headers/prefs.h
alert.$O: $(SRC_TOP)Headers/note_types.h
alert.$O: $(SRC_TOP)Headers/tune.h
alert.$O: $(SRC_TOP)Headers/tune_types.h
alert.$O: $(SRC_TOP)Headers/tune_build.h
alert.$O: $(SRC_TOP)Headers/message.h
alert.$O: $(SRC_TOP)Headers/brl_dots.h

# Dependencies for api_control.$O:
api_control.$O: $(SRC_DIR)/api_control.c
api_control.$O: $(SRC_TOP)Headers/prologue.h
api_control.$O: $(BLD_TOP)config.h
api_control.$O: $(BLD_TOP)forbuild.h
api_control.$O: $(SRC_TOP)Headers/log.h
api_control.$O: $(SRC_TOP)Headers/api_types.h
api_control.$O: $(SRC_TOP)Headers/ktb_types.h
api_control.$O: $(SRC_DIR)/api_control.h
api_control.$O: $(SRC_TOP)Headers/async.h
api_control.$O: $(SRC_TOP)Headers/brl_types.h
api_control.$O: $(SRC_TOP)Headers/driver.h
api_control.$O: $(SRC_TOP)Headers/gio_types.h
api_control.$O: $(SRC_TOP)Headers/queue.h
api_control.$O: $(SRC_TOP)Headers/serial_types.h
api_control.$O: $(SRC_TOP)Headers/usb_types.h
api_control.$O: $(SRC_DIR)/api_server.h
api_control.$O: $(SRC_DIR)/brl.h
api_control.$O: $(SRC_TOP)Headers/cmd.h
api_control.$O: $(SRC_TOP)Headers/cmd_types.h
api_control.$O: $(SRC_TOP)Headers/ctb.h
api_control.$O: $(SRC_TOP)Headers/ctb_types.h
api_control.$O: $(SRC_TOP)Headers/ktb.h
api_control.$O: $(SRC_TOP)Headers/pid.h
api_control.$O: $(SRC_TOP)Headers/prefs.h
api_control.$O: $(SRC_TOP)Headers/program.h
api_control.$O: $(SRC_TOP)Headers/scr_types.h
api_control.$O: $(SRC_TOP)Headers/spk.h
api_control.$O: $(SRC_TOP)Headers/spk_types.h
api_control.$O: $(SRC_TOP)Headers/strfmth.h
api_control.$O: $(SRC_TOP)Headers/timing.h
api_control.$O: $(SRC_DIR)/core.h
api_control.$O: $(SRC_DIR)/profile_types.h
api_control.$O: $(SRC_DIR)/ses.h

# Dependencies for apitest.$O:
apitest.$O: $(SRC_DIR)/apitest.c
apitest.$O: $(SRC_TOP)Headers/prologue.h
apitest.$O: $(BLD_TOP)config.h
apitest.$O: $(BLD_TOP)forbuild.h
apitest.$O: $(SRC_TOP)Headers/timing.h
apitest.$O: $(SRC_TOP)Headers/win_pthread.h
apitest.$O: $(SRC_TOP)Headers/datafile.h
apitest.$O: $(SRC_TOP)Headers/options.h
apitest.$O: $(SRC_TOP)Headers/pid.h
apitest.$O: $(SRC_TOP)Headers/program.h
apitest.$O: $(SRC_TOP)Headers/strfmth.h
apitest.$O: $(SRC_TOP)Headers/variables.h
apitest.$O: $(SRC_TOP)Headers/brl_cmds.h
apitest.$O: $(SRC_TOP)Headers/brl_dots.h
apitest.$O: $(SRC_TOP)Headers/cmd.h
apitest.$O: $(SRC_TOP)Headers/cmd_types.h
apitest.$O: brlapi_constants.h
apitest.$O: $(SRC_DIR)/brlapi_keycodes.h
apitest.$O: $(SRC_DIR)/cmd_brlapi.h
apitest.$O: $(SRC_TOP)Headers/async.h
apitest.$O: $(SRC_TOP)Headers/async_wait.h
apitest.$O: brlapi.h

# Dependencies for async_alarm.$O:
async_alarm.$O: $(SRC_DIR)/async_alarm.c
async_alarm.$O: $(SRC_TOP)Headers/prologue.h
async_alarm.$O: $(BLD_TOP)config.h
async_alarm.$O: $(BLD_TOP)forbuild.h
async_alarm.$O: $(SRC_TOP)Headers/log.h
async_alarm.$O: $(SRC_TOP)Headers/async.h
async_alarm.$O: $(SRC_TOP)Headers/async_alarm.h
async_alarm.$O: $(SRC_TOP)Headers/timing.h
async_alarm.$O: $(SRC_TOP)Headers/queue.h
async_alarm.$O: $(SRC_DIR)/async_internal.h

# Dependencies for async_data.$O:
async_data.$O: $(SRC_DIR)/async_data.c
async_data.$O: $(SRC_TOP)Headers/prologue.h
async_data.$O: $(BLD_TOP)config.h
async_data.$O: $(BLD_TOP)forbuild.h
async_data.$O: $(SRC_TOP)Headers/log.h
async_data.$O: $(SRC_TOP)Headers/get_pthreads.h
async_data.$O: $(SRC_TOP)Headers/thread.h
async_data.$O: $(SRC_TOP)Headers/timing.h
async_data.$O: $(SRC_TOP)Headers/win_pthread.h
async_data.$O: $(SRC_TOP)Headers/async.h
async_data.$O: $(SRC_TOP)Headers/queue.h
async_data.$O: $(SRC_DIR)/async_internal.h

# Dependencies for async_event.$O:
async_event.$O: $(SRC_DIR)/async_event.c
async_event.$O: $(SRC_TOP)Headers/prologue.h
async_event.$O: $(BLD_TOP)config.h
async_event.$O: $(BLD_TOP)forbuild.h
async_event.$O: $(SRC_TOP)Headers/log.h
async_event.$O: $(SRC_TOP)Headers/async.h
async_event.$O: $(SRC_TOP)Headers/async_io.h
async_event.$O: $(SRC_TOP)Headers/async_event.h
async_event.$O: $(SRC_TOP)Headers/queue.h
async_event.$O: $(SRC_DIR)/async_internal.h
async_event.$O: $(SRC_TOP)Headers/file.h
async_event.$O: $(SRC_TOP)Headers/get_sockets.h

# Dependencies for async_handle.$O:
async_handle.$O: $(SRC_DIR)/async_handle.c
async_handle.$O: $(SRC_TOP)Headers/prologue.h
async_handle.$O: $(BLD_TOP)config.h
async_handle.$O: $(BLD_TOP)forbuild.h
async_handle.$O: $(SRC_TOP)Headers/log.h
async_handle.$O: $(SRC_TOP)Headers/async.h
async_handle.$O: $(SRC_TOP)Headers/queue.h
async_handle.$O: $(SRC_DIR)/async_internal.h

# Dependencies for async_io.$O:
async_io.$O: $(SRC_DIR)/async_io.c
async_io.$O: $(SRC_TOP)Headers/prologue.h
async_io.$O: $(BLD_TOP)config.h
async_io.$O: $(BLD_TOP)forbuild.h
async_io.$O: $(SRC_TOP)Headers/get_select.h
async_io.$O: $(SRC_TOP)Headers/system_msdos.h
async_io.$O: $(SRC_TOP)Headers/log.h
async_io.$O: $(SRC_TOP)Headers/async.h
async_io.$O: $(SRC_TOP)Headers/async_io.h
async_io.$O: $(SRC_TOP)Headers/queue.h
async_io.$O: $(SRC_DIR)/async_internal.h
async_io.$O: $(SRC_TOP)Headers/timing.h

# Dependencies for async_signal.$O:
async_signal.$O: $(SRC_DIR)/async_signal.c
async_signal.$O: $(SRC_TOP)Headers/prologue.h
async_signal.$O: $(BLD_TOP)config.h
async_signal.$O: $(BLD_TOP)forbuild.h
async_signal.$O: $(SRC_TOP)Headers/log.h
async_signal.$O: $(SRC_TOP)Headers/async.h
async_signal.$O: $(SRC_TOP)Headers/async_event.h
async_signal.$O: $(SRC_TOP)Headers/async_signal.h
async_signal.$O: $(SRC_TOP)Headers/queue.h
async_signal.$O: $(SRC_DIR)/async_internal.h
async_signal.$O: $(SRC_TOP)Headers/get_pthreads.h
async_signal.$O: $(SRC_TOP)Headers/timing.h
async_signal.$O: $(SRC_TOP)Headers/win_pthread.h
async_signal.$O: $(SRC_TOP)Headers/async_io.h

# Dependencies for async_task.$O:
async_task.$O: $(SRC_DIR)/async_task.c
async_task.$O: $(SRC_TOP)Headers/prologue.h
async_task.$O: $(BLD_TOP)config.h
async_task.$O: $(BLD_TOP)forbuild.h
async_task.$O: $(SRC_TOP)Headers/log.h
async_task.$O: $(SRC_TOP)Headers/async.h
async_task.$O: $(SRC_TOP)Headers/async_event.h
async_task.$O: $(SRC_TOP)Headers/async_task.h
async_task.$O: $(SRC_TOP)Headers/queue.h
async_task.$O: $(SRC_DIR)/async_internal.h

# Dependencies for async_wait.$O:
async_wait.$O: $(SRC_DIR)/async_wait.c
async_wait.$O: $(SRC_TOP)Headers/prologue.h
async_wait.$O: $(BLD_TOP)config.h
async_wait.$O: $(BLD_TOP)forbuild.h
async_wait.$O: $(SRC_TOP)Headers/log.h
async_wait.$O: $(SRC_TOP)Headers/async.h
async_wait.$O: $(SRC_TOP)Headers/async_wait.h
async_wait.$O: $(SRC_TOP)Headers/queue.h
async_wait.$O: $(SRC_DIR)/async_internal.h
async_wait.$O: $(SRC_TOP)Headers/timing.h

# Dependencies for atb_compile.$O:
atb_compile.$O: $(SRC_DIR)/atb_compile.c
atb_compile.$O: $(SRC_TOP)Headers/prologue.h
atb_compile.$O: $(BLD_TOP)config.h
atb_compile.$O: $(BLD_TOP)forbuild.h
atb_compile.$O: $(SRC_TOP)Headers/file.h
atb_compile.$O: $(SRC_TOP)Headers/get_sockets.h
atb_compile.$O: $(SRC_TOP)Headers/datafile.h
atb_compile.$O: $(SRC_TOP)Headers/variables.h
atb_compile.$O: $(SRC_TOP)Headers/dataarea.h
atb_compile.$O: $(SRC_TOP)Headers/atb.h
atb_compile.$O: $(SRC_DIR)/atb_internal.h

# Dependencies for atb_translate.$O:
atb_translate.$O: $(SRC_DIR)/atb_translate.c
atb_translate.$O: $(SRC_TOP)Headers/prologue.h
atb_translate.$O: $(BLD_TOP)config.h
atb_translate.$O: $(BLD_TOP)forbuild.h
atb_translate.$O: $(SRC_TOP)Headers/log.h
atb_translate.$O: $(SRC_TOP)Headers/file.h
atb_translate.$O: $(SRC_TOP)Headers/get_sockets.h
atb_translate.$O: $(SRC_TOP)Headers/atb.h
atb_translate.$O: $(SRC_DIR)/atb_internal.h
atb_translate.$O: attr.auto.h

# Dependencies for auth.$O:
auth.$O: $(SRC_DIR)/auth.c
auth.$O: $(SRC_TOP)Headers/prologue.h
auth.$O: $(BLD_TOP)config.h
auth.$O: $(BLD_TOP)forbuild.h
auth.$O: $(SRC_TOP)Headers/log.h
auth.$O: $(SRC_TOP)Headers/strfmt.h
auth.$O: $(SRC_TOP)Headers/strfmth.h
auth.$O: $(SRC_TOP)Headers/parse.h
auth.$O: $(SRC_TOP)Headers/auth.h
auth.$O: $(SRC_TOP)Headers/async.h
auth.$O: $(SRC_TOP)Headers/async_wait.h

# Dependencies for beep.$O:
beep.$O: $(SRC_DIR)/beep.c
beep.$O: $(SRC_TOP)Headers/prologue.h
beep.$O: $(BLD_TOP)config.h
beep.$O: $(BLD_TOP)forbuild.h
beep.$O: $(SRC_TOP)Headers/async.h
beep.$O: $(SRC_TOP)Headers/async_wait.h
beep.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for beep_linux.$O:
beep_linux.$O: $(SRC_DIR)/beep_linux.c
beep_linux.$O: $(SRC_TOP)Headers/prologue.h
beep_linux.$O: $(BLD_TOP)config.h
beep_linux.$O: $(BLD_TOP)forbuild.h
beep_linux.$O: $(SRC_TOP)Headers/log.h
beep_linux.$O: $(SRC_TOP)Headers/beep.h
beep_linux.$O: $(SRC_TOP)Headers/system_linux.h

# Dependencies for beep_msdos.$O:
beep_msdos.$O: $(SRC_DIR)/beep_msdos.c
beep_msdos.$O: $(SRC_TOP)Headers/prologue.h
beep_msdos.$O: $(BLD_TOP)config.h
beep_msdos.$O: $(BLD_TOP)forbuild.h
beep_msdos.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for beep_none.$O:
beep_none.$O: $(SRC_DIR)/beep_none.c
beep_none.$O: $(SRC_TOP)Headers/prologue.h
beep_none.$O: $(BLD_TOP)config.h
beep_none.$O: $(BLD_TOP)forbuild.h
beep_none.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for beep_solaris.$O:
beep_solaris.$O: $(SRC_DIR)/beep_solaris.c
beep_solaris.$O: $(SRC_TOP)Headers/prologue.h
beep_solaris.$O: $(BLD_TOP)config.h
beep_solaris.$O: $(BLD_TOP)forbuild.h
beep_solaris.$O: $(SRC_TOP)Headers/log.h
beep_solaris.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for beep_spkr.$O:
beep_spkr.$O: $(SRC_DIR)/beep_spkr.c
beep_spkr.$O: $(SRC_TOP)Headers/prologue.h
beep_spkr.$O: $(BLD_TOP)config.h
beep_spkr.$O: $(BLD_TOP)forbuild.h
beep_spkr.$O: $(SRC_TOP)Headers/log.h
beep_spkr.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for beep_windows.$O:
beep_windows.$O: $(SRC_DIR)/beep_windows.c
beep_windows.$O: $(SRC_TOP)Headers/prologue.h
beep_windows.$O: $(BLD_TOP)config.h
beep_windows.$O: $(BLD_TOP)forbuild.h
beep_windows.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for beep_wskbd.$O:
beep_wskbd.$O: $(SRC_DIR)/beep_wskbd.c
beep_wskbd.$O: $(SRC_TOP)Headers/prologue.h
beep_wskbd.$O: $(BLD_TOP)config.h
beep_wskbd.$O: $(BLD_TOP)forbuild.h
beep_wskbd.$O: $(SRC_TOP)Headers/log.h
beep_wskbd.$O: $(SRC_TOP)Headers/device.h
beep_wskbd.$O: $(SRC_TOP)Headers/beep.h

# Dependencies for bell.$O:
bell.$O: $(SRC_DIR)/bell.c
bell.$O: $(SRC_TOP)Headers/prologue.h
bell.$O: $(BLD_TOP)config.h
bell.$O: $(BLD_TOP)forbuild.h
bell.$O: $(SRC_TOP)Headers/bell.h
bell.$O: $(SRC_TOP)Headers/alert.h
bell.$O: $(SRC_TOP)Headers/prefs.h
bell.$O: $(SRC_TOP)Headers/tune_types.h

# Dependencies for bell_linux.$O:
bell_linux.$O: $(SRC_DIR)/bell_linux.c
bell_linux.$O: $(SRC_TOP)Headers/prologue.h
bell_linux.$O: $(BLD_TOP)config.h
bell_linux.$O: $(BLD_TOP)forbuild.h
bell_linux.$O: $(SRC_TOP)Headers/bell.h
bell_linux.$O: $(SRC_TOP)Headers/system_linux.h

# Dependencies for bell_none.$O:
bell_none.$O: $(SRC_DIR)/bell_none.c
bell_none.$O: $(SRC_TOP)Headers/prologue.h
bell_none.$O: $(BLD_TOP)config.h
bell_none.$O: $(BLD_TOP)forbuild.h
bell_none.$O: $(SRC_TOP)Headers/bell.h

# Dependencies for blink.$O:
blink.$O: $(SRC_DIR)/blink.c
blink.$O: $(SRC_TOP)Headers/prologue.h
blink.$O: $(BLD_TOP)config.h
blink.$O: $(BLD_TOP)forbuild.h
blink.$O: $(SRC_DIR)/blink.h
blink.$O: $(SRC_TOP)Headers/prefs.h
blink.$O: $(SRC_TOP)Headers/async.h
blink.$O: $(SRC_TOP)Headers/async_alarm.h
blink.$O: $(SRC_TOP)Headers/timing.h
blink.$O: $(SRC_TOP)Headers/api_types.h
blink.$O: $(SRC_TOP)Headers/brl_types.h
blink.$O: $(SRC_TOP)Headers/driver.h
blink.$O: $(SRC_TOP)Headers/gio_types.h
blink.$O: $(SRC_TOP)Headers/ktb_types.h
blink.$O: $(SRC_TOP)Headers/queue.h
blink.$O: $(SRC_TOP)Headers/serial_types.h
blink.$O: $(SRC_TOP)Headers/usb_types.h
blink.$O: $(SRC_DIR)/update.h
blink.$O: $(SRC_TOP)Headers/cmd.h
blink.$O: $(SRC_TOP)Headers/cmd_types.h
blink.$O: $(SRC_TOP)Headers/ctb.h
blink.$O: $(SRC_TOP)Headers/ctb_types.h
blink.$O: $(SRC_TOP)Headers/ktb.h
blink.$O: $(SRC_TOP)Headers/pid.h
blink.$O: $(SRC_TOP)Headers/program.h
blink.$O: $(SRC_TOP)Headers/scr_types.h
blink.$O: $(SRC_TOP)Headers/spk.h
blink.$O: $(SRC_TOP)Headers/spk_types.h
blink.$O: $(SRC_TOP)Headers/strfmth.h
blink.$O: $(SRC_DIR)/brl.h
blink.$O: $(SRC_DIR)/core.h
blink.$O: $(SRC_DIR)/profile_types.h
blink.$O: $(SRC_DIR)/ses.h

# Dependencies for bluetooth.$O:
bluetooth.$O: $(SRC_DIR)/bluetooth.c
bluetooth.$O: $(SRC_TOP)Headers/prologue.h
bluetooth.$O: $(BLD_TOP)config.h
bluetooth.$O: $(BLD_TOP)forbuild.h
bluetooth.$O: $(SRC_TOP)Headers/log.h
bluetooth.$O: $(SRC_DIR)/parameters.h
bluetooth.$O: $(SRC_TOP)Headers/timing.h
bluetooth.$O: $(SRC_TOP)Headers/async.h
bluetooth.$O: $(SRC_TOP)Headers/async_wait.h
bluetooth.$O: $(SRC_TOP)Headers/parse.h
bluetooth.$O: $(SRC_TOP)Headers/device.h
bluetooth.$O: $(SRC_TOP)Headers/queue.h
bluetooth.$O: $(SRC_TOP)Headers/async_io.h
bluetooth.$O: $(SRC_TOP)Headers/io_bluetooth.h
bluetooth.$O: $(SRC_DIR)/bluetooth_internal.h

# Dependencies for bluetooth_android.$O:
bluetooth_android.$O: $(SRC_DIR)/bluetooth_android.c
bluetooth_android.$O: $(SRC_TOP)Headers/prologue.h
bluetooth_android.$O: $(BLD_TOP)config.h
bluetooth_android.$O: $(BLD_TOP)forbuild.h
bluetooth_android.$O: $(SRC_TOP)Headers/async.h
bluetooth_android.$O: $(SRC_TOP)Headers/async_io.h
bluetooth_android.$O: $(SRC_TOP)Headers/io_bluetooth.h
bluetooth_android.$O: $(SRC_DIR)/bluetooth_internal.h
bluetooth_android.$O: $(SRC_TOP)Headers/log.h
bluetooth_android.$O: $(SRC_TOP)Headers/get_sockets.h
bluetooth_android.$O: $(SRC_TOP)Headers/io_misc.h
bluetooth_android.$O: $(SRC_TOP)Headers/get_pthreads.h
bluetooth_android.$O: $(SRC_TOP)Headers/thread.h
bluetooth_android.$O: $(SRC_TOP)Headers/timing.h
bluetooth_android.$O: $(SRC_TOP)Headers/win_pthread.h
bluetooth_android.$O: $(SRC_TOP)Headers/common_java.h
bluetooth_android.$O: $(SRC_TOP)Headers/system_java.h

# Dependencies for bluetooth_darwin.$O:
bluetooth_darwin.$O: $(SRC_DIR)/bluetooth_darwin.c
bluetooth_darwin.$O: $(SRC_TOP)Headers/prologue.h
bluetooth_darwin.$O: $(BLD_TOP)config.h
bluetooth_darwin.$O: $(BLD_TOP)forbuild.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/log.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/get_sockets.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/io_misc.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/async.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/async_io.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/io_bluetooth.h
bluetooth_darwin.$O: $(SRC_DIR)/bluetooth_internal.h
bluetooth_darwin.$O: $(SRC_TOP)Headers/system_darwin.h

# Dependencies for bluetooth_linux.$O:
bluetooth_linux.$O: $(SRC_DIR)/bluetooth_linux.c
bluetooth_linux.$O: $(SRC_TOP)Headers/prologue.h
bluetooth_linux.$O: $(BLD_TOP)config.h
bluetooth_linux.$O: $(BLD_TOP)forbuild.h
bluetooth_linux.$O: $(SRC_TOP)Headers/log.h
bluetooth_linux.$O: $(SRC_DIR)/parameters.h
bluetooth_linux.$O: $(SRC_TOP)Headers/async.h
bluetooth_linux.$O: $(SRC_TOP)Headers/async_io.h
bluetooth_linux.$O: $(SRC_TOP)Headers/io_bluetooth.h
bluetooth_linux.$O: $(SRC_DIR)/bluetooth_internal.h
bluetooth_linux.$O: $(SRC_TOP)Headers/get_sockets.h
bluetooth_linux.$O: $(SRC_TOP)Headers/io_misc.h
bluetooth_linux.$O: $(SRC_TOP)Headers/timing.h

# Dependencies for bluetooth_names.$O:
bluetooth_names.$O: $(SRC_DIR)/bluetooth_names.c
bluetooth_names.$O: $(SRC_TOP)Headers/prologue.h
bluetooth_names.$O: $(BLD_TOP)config.h
bluetooth_names.$O: $(BLD_TOP)forbuild.h
bluetooth_names.$O: $(SRC_DIR)/bluetooth_internal.h

# Dependencies for bluetooth_none.$O:
bluetooth_none.$O: $(SRC_DIR)/bluetooth_none.c
bluetooth_none.$O: $(SRC_TOP)Headers/prologue.h
bluetooth_none.$O: $(BLD_TOP)config.h
bluetooth_none.$O: $(BLD_TOP)forbuild.h
bluetooth_none.$O: $(SRC_TOP)Headers/async.h
bluetooth_none.$O: $(SRC_TOP)Headers/async_io.h
bluetooth_none.$O: $(SRC_TOP)Headers/io_bluetooth.h
bluetooth_none.$O: $(SRC_DIR)/bluetooth_internal.h
bluetooth_none.$O: $(SRC_TOP)Headers/log.h

# Dependencies for bluetooth_windows.$O:
bluetooth_windows.$O: $(SRC_DIR)/bluetooth_windows.c
bluetooth_windows.$O: $(SRC_TOP)Headers/prologue.h
bluetooth_windows.$O: $(BLD_TOP)config.h
bluetooth_windows.$O: $(BLD_TOP)forbuild.h
bluetooth_windows.$O: $(SRC_TOP)Headers/log.h
bluetooth_windows.$O: $(SRC_TOP)Headers/timing.h
bluetooth_windows.$O: $(SRC_TOP)Headers/bitfield.h
bluetooth_windows.$O: $(SRC_TOP)Headers/async.h
bluetooth_windows.$O: $(SRC_TOP)Headers/async_io.h
bluetooth_windows.$O: $(SRC_TOP)Headers/io_bluetooth.h
bluetooth_windows.$O: $(SRC_DIR)/bluetooth_internal.h

# Dependencies for brl.$O:
brl.$O: $(SRC_DIR)/brl.c
brl.$O: $(SRC_TOP)Headers/prologue.h
brl.$O: $(BLD_TOP)config.h
brl.$O: $(BLD_TOP)forbuild.h
brl.$O: $(SRC_TOP)Headers/log.h
brl.$O: $(SRC_DIR)/parameters.h
brl.$O: $(SRC_TOP)Headers/charset.h
brl.$O: $(SRC_TOP)Headers/lock.h
brl.$O: $(SRC_TOP)Headers/unicode.h
brl.$O: $(SRC_TOP)Headers/api_types.h
brl.$O: $(SRC_TOP)Headers/async.h
brl.$O: $(SRC_TOP)Headers/brl_types.h
brl.$O: $(SRC_TOP)Headers/driver.h
brl.$O: $(SRC_TOP)Headers/gio_types.h
brl.$O: $(SRC_TOP)Headers/ktb_types.h
brl.$O: $(SRC_TOP)Headers/queue.h
brl.$O: $(SRC_TOP)Headers/serial_types.h
brl.$O: $(SRC_TOP)Headers/usb_types.h
brl.$O: $(SRC_DIR)/brl.h
brl.$O: $(SRC_TOP)Headers/ttb.h
brl.$O: $(SRC_TOP)Headers/ktb.h

# Dependencies for brl_base.$O:
brl_base.$O: $(SRC_DIR)/brl_base.c
brl_base.$O: $(SRC_TOP)Headers/prologue.h
brl_base.$O: $(BLD_TOP)config.h
brl_base.$O: $(BLD_TOP)forbuild.h
brl_base.$O: $(SRC_TOP)Headers/log.h
brl_base.$O: $(SRC_TOP)Headers/report.h
brl_base.$O: $(SRC_TOP)Headers/queue.h
brl_base.$O: $(SRC_TOP)Headers/async.h
brl_base.$O: $(SRC_TOP)Headers/async_alarm.h
brl_base.$O: $(SRC_TOP)Headers/timing.h
brl_base.$O: $(SRC_TOP)Headers/api_types.h
brl_base.$O: $(SRC_TOP)Headers/brl_base.h
brl_base.$O: $(SRC_TOP)Headers/brl_types.h
brl_base.$O: $(SRC_TOP)Headers/driver.h
brl_base.$O: $(SRC_TOP)Headers/gio_types.h
brl_base.$O: $(SRC_TOP)Headers/ktb_types.h
brl_base.$O: $(SRC_TOP)Headers/serial_types.h
brl_base.$O: $(SRC_TOP)Headers/usb_types.h
brl_base.$O: $(SRC_TOP)Headers/brl_utils.h
brl_base.$O: $(SRC_TOP)Headers/brl_dots.h
brl_base.$O: $(SRC_TOP)Headers/prefs.h
brl_base.$O: $(SRC_TOP)Headers/kbd_keycodes.h
brl_base.$O: $(SRC_TOP)Headers/async_io.h
brl_base.$O: $(SRC_TOP)Headers/io_generic.h
brl_base.$O: $(SRC_DIR)/cmd_queue.h
brl_base.$O: $(SRC_TOP)Headers/ktb.h

# Dependencies for brl_driver.$O:
brl_driver.$O: $(SRC_DIR)/brl_driver.c
brl_driver.$O: $(SRC_TOP)Headers/prologue.h
brl_driver.$O: $(BLD_TOP)config.h
brl_driver.$O: $(BLD_TOP)forbuild.h
brl_driver.$O: $(SRC_TOP)Headers/log.h
brl_driver.$O: $(SRC_TOP)Headers/driver.h
brl_driver.$O: $(SRC_TOP)Headers/drivers.h
brl_driver.$O: $(SRC_TOP)Headers/api_types.h
brl_driver.$O: $(SRC_TOP)Headers/async.h
brl_driver.$O: $(SRC_TOP)Headers/brl_types.h
brl_driver.$O: $(SRC_TOP)Headers/gio_types.h
brl_driver.$O: $(SRC_TOP)Headers/ktb_types.h
brl_driver.$O: $(SRC_TOP)Headers/queue.h
brl_driver.$O: $(SRC_TOP)Headers/serial_types.h
brl_driver.$O: $(SRC_TOP)Headers/usb_types.h
brl_driver.$O: $(SRC_DIR)/brl.h
brl_driver.$O: brl.auto.h
brl_driver.$O: $(SRC_TOP)Headers/async_io.h
brl_driver.$O: $(SRC_TOP)Headers/brl_base.h
brl_driver.$O: $(SRC_TOP)Headers/brl_cmds.h
brl_driver.$O: $(SRC_TOP)Headers/brl_dots.h
brl_driver.$O: $(SRC_TOP)Headers/brl_driver.h
brl_driver.$O: $(SRC_TOP)Headers/brl_utils.h
brl_driver.$O: $(SRC_TOP)Headers/cmd_enqueue.h
brl_driver.$O: $(SRC_TOP)Headers/io_generic.h
brl_driver.$O: $(SRC_TOP)Headers/status_types.h

# Dependencies for brl_input.$O:
brl_input.$O: $(SRC_DIR)/brl_input.c
brl_input.$O: $(SRC_TOP)Headers/prologue.h
brl_input.$O: $(BLD_TOP)config.h
brl_input.$O: $(BLD_TOP)forbuild.h
brl_input.$O: $(SRC_TOP)Headers/log.h
brl_input.$O: $(SRC_TOP)Headers/embed.h
brl_input.$O: $(SRC_TOP)Headers/pid.h
brl_input.$O: $(SRC_TOP)Headers/program.h
brl_input.$O: $(SRC_DIR)/parameters.h
brl_input.$O: $(SRC_DIR)/brl_input.h
brl_input.$O: $(SRC_TOP)Headers/api_types.h
brl_input.$O: $(SRC_TOP)Headers/async.h
brl_input.$O: $(SRC_TOP)Headers/brl_types.h
brl_input.$O: $(SRC_TOP)Headers/brl_utils.h
brl_input.$O: $(SRC_TOP)Headers/driver.h
brl_input.$O: $(SRC_TOP)Headers/gio_types.h
brl_input.$O: $(SRC_TOP)Headers/ktb_types.h
brl_input.$O: $(SRC_TOP)Headers/queue.h
brl_input.$O: $(SRC_TOP)Headers/serial_types.h
brl_input.$O: $(SRC_TOP)Headers/usb_types.h
brl_input.$O: $(SRC_TOP)Headers/brl_cmds.h
brl_input.$O: $(SRC_TOP)Headers/brl_dots.h
brl_input.$O: $(SRC_DIR)/cmd_queue.h
brl_input.$O: $(SRC_TOP)Headers/cmd_enqueue.h
brl_input.$O: $(SRC_TOP)Headers/async_io.h
brl_input.$O: $(SRC_TOP)Headers/io_generic.h
brl_input.$O: $(SRC_TOP)Headers/cmd.h
brl_input.$O: $(SRC_TOP)Headers/cmd_types.h
brl_input.$O: $(SRC_TOP)Headers/ctb.h
brl_input.$O: $(SRC_TOP)Headers/ctb_types.h
brl_input.$O: $(SRC_TOP)Headers/ktb.h
brl_input.$O: $(SRC_TOP)Headers/prefs.h
brl_input.$O: $(SRC_TOP)Headers/scr_types.h
brl_input.$O: $(SRC_TOP)Headers/spk.h
brl_input.$O: $(SRC_TOP)Headers/spk_types.h
brl_input.$O: $(SRC_TOP)Headers/strfmth.h
brl_input.$O: $(SRC_TOP)Headers/timing.h
brl_input.$O: $(SRC_DIR)/brl.h
brl_input.$O: $(SRC_DIR)/core.h
brl_input.$O: $(SRC_DIR)/profile_types.h
brl_input.$O: $(SRC_DIR)/ses.h

# Dependencies for brl_utils.$O:
brl_utils.$O: $(SRC_DIR)/brl_utils.c
brl_utils.$O: $(SRC_TOP)Headers/prologue.h
brl_utils.$O: $(BLD_TOP)config.h
brl_utils.$O: $(BLD_TOP)forbuild.h
brl_utils.$O: $(SRC_TOP)Headers/log.h
brl_utils.$O: $(SRC_TOP)Headers/report.h
brl_utils.$O: $(SRC_TOP)Headers/api_types.h
brl_utils.$O: $(SRC_TOP)Headers/async.h
brl_utils.$O: $(SRC_TOP)Headers/brl_types.h
brl_utils.$O: $(SRC_TOP)Headers/brl_utils.h
brl_utils.$O: $(SRC_TOP)Headers/driver.h
brl_utils.$O: $(SRC_TOP)Headers/gio_types.h
brl_utils.$O: $(SRC_TOP)Headers/ktb_types.h
brl_utils.$O: $(SRC_TOP)Headers/queue.h
brl_utils.$O: $(SRC_TOP)Headers/serial_types.h
brl_utils.$O: $(SRC_TOP)Headers/usb_types.h
brl_utils.$O: $(SRC_TOP)Headers/brl_dots.h
brl_utils.$O: $(SRC_TOP)Headers/async_wait.h
brl_utils.$O: $(SRC_TOP)Headers/ktb.h

# Dependencies for brlapi_client.$O:
brlapi_client.$O: $(SRC_DIR)/brlapi_client.c
brlapi_client.$O: $(SRC_TOP)Headers/prologue.h
brlapi_client.$O: $(BLD_TOP)config.h
brlapi_client.$O: $(BLD_TOP)forbuild.h
brlapi_client.$O: $(SRC_TOP)Headers/timing.h
brlapi_client.$O: $(SRC_TOP)Headers/win_pthread.h
brlapi_client.$O: $(SRC_TOP)Headers/win_errno.h
brlapi_client.$O: brlapi.h
brlapi_client.$O: brlapi_constants.h
brlapi_client.$O: $(SRC_DIR)/brlapi_keycodes.h
brlapi_client.$O: $(SRC_DIR)/brlapi_protocol.h
brlapi_client.$O: $(SRC_DIR)/brlapi_common.h
brlapi_client.$O: brlapi_keytab.auto.h

# Dependencies for brlapi_keyranges.$O:
brlapi_keyranges.$O: $(SRC_DIR)/brlapi_keyranges.c
brlapi_keyranges.$O: $(SRC_TOP)Headers/prologue.h
brlapi_keyranges.$O: $(BLD_TOP)config.h
brlapi_keyranges.$O: $(BLD_TOP)forbuild.h
brlapi_keyranges.$O: $(SRC_DIR)/brlapi_keyranges.h
brlapi_keyranges.$O: $(SRC_TOP)Headers/log.h

# Dependencies for brlapi_server.$O:
brlapi_server.$O: $(SRC_DIR)/brlapi_server.c
brlapi_server.$O: $(SRC_TOP)Headers/prologue.h
brlapi_server.$O: $(BLD_TOP)config.h
brlapi_server.$O: $(BLD_TOP)forbuild.h
brlapi_server.$O: $(SRC_TOP)Headers/system_windows.h
brlapi_server.$O: $(SRC_TOP)Headers/timing.h
brlapi_server.$O: $(SRC_TOP)Headers/win_pthread.h
brlapi_server.$O: brlapi.h
brlapi_server.$O: brlapi_constants.h
brlapi_server.$O: $(SRC_DIR)/brlapi_keycodes.h
brlapi_server.$O: $(SRC_DIR)/brlapi_protocol.h
brlapi_server.$O: $(SRC_DIR)/brlapi_keyranges.h
brlapi_server.$O: $(SRC_DIR)/cmd_brlapi.h
brlapi_server.$O: $(SRC_TOP)Headers/brl_cmds.h
brlapi_server.$O: $(SRC_TOP)Headers/brl_dots.h
brlapi_server.$O: $(SRC_TOP)Headers/api_types.h
brlapi_server.$O: $(SRC_TOP)Headers/async.h
brlapi_server.$O: $(SRC_TOP)Headers/brl_types.h
brlapi_server.$O: $(SRC_TOP)Headers/brl_utils.h
brlapi_server.$O: $(SRC_TOP)Headers/driver.h
brlapi_server.$O: $(SRC_TOP)Headers/gio_types.h
brlapi_server.$O: $(SRC_TOP)Headers/ktb_types.h
brlapi_server.$O: $(SRC_TOP)Headers/queue.h
brlapi_server.$O: $(SRC_TOP)Headers/serial_types.h
brlapi_server.$O: $(SRC_TOP)Headers/usb_types.h
brlapi_server.$O: $(SRC_TOP)Headers/embed.h
brlapi_server.$O: $(SRC_TOP)Headers/pid.h
brlapi_server.$O: $(SRC_TOP)Headers/program.h
brlapi_server.$O: $(SRC_TOP)Headers/ttb.h
brlapi_server.$O: $(SRC_TOP)Headers/cmd.h
brlapi_server.$O: $(SRC_TOP)Headers/cmd_types.h
brlapi_server.$O: $(SRC_TOP)Headers/ctb.h
brlapi_server.$O: $(SRC_TOP)Headers/ctb_types.h
brlapi_server.$O: $(SRC_TOP)Headers/ktb.h
brlapi_server.$O: $(SRC_TOP)Headers/prefs.h
brlapi_server.$O: $(SRC_TOP)Headers/scr_types.h
brlapi_server.$O: $(SRC_TOP)Headers/spk.h
brlapi_server.$O: $(SRC_TOP)Headers/spk_types.h
brlapi_server.$O: $(SRC_TOP)Headers/strfmth.h
brlapi_server.$O: $(SRC_DIR)/brl.h
brlapi_server.$O: $(SRC_DIR)/core.h
brlapi_server.$O: $(SRC_DIR)/profile_types.h
brlapi_server.$O: $(SRC_DIR)/ses.h
brlapi_server.$O: $(SRC_DIR)/api_server.h
brlapi_server.$O: $(SRC_TOP)Headers/report.h
brlapi_server.$O: $(SRC_TOP)Headers/log.h
brlapi_server.$O: $(SRC_TOP)Headers/addresses.h
brlapi_server.$O: $(SRC_TOP)Headers/file.h
brlapi_server.$O: $(SRC_TOP)Headers/get_sockets.h
brlapi_server.$O: $(SRC_TOP)Headers/parse.h
brlapi_server.$O: $(SRC_TOP)Headers/auth.h
brlapi_server.$O: $(SRC_TOP)Headers/io_misc.h
brlapi_server.$O: $(SRC_DIR)/scr.h
brlapi_server.$O: $(SRC_TOP)Headers/charset.h
brlapi_server.$O: $(SRC_TOP)Headers/lock.h
brlapi_server.$O: $(SRC_TOP)Headers/async_event.h
brlapi_server.$O: $(SRC_TOP)Headers/async_signal.h
brlapi_server.$O: $(SRC_TOP)Headers/get_pthreads.h
brlapi_server.$O: $(SRC_TOP)Headers/thread.h
brlapi_server.$O: $(SRC_DIR)/blink.h
brlapi_server.$O: $(SRC_DIR)/brlapi_common.h

# Dependencies for brltest.$O:
brltest.$O: $(SRC_DIR)/brltest.c
brltest.$O: $(SRC_TOP)Headers/prologue.h
brltest.$O: $(BLD_TOP)config.h
brltest.$O: $(BLD_TOP)forbuild.h
brltest.$O: $(SRC_TOP)Headers/pid.h
brltest.$O: $(SRC_TOP)Headers/program.h
brltest.$O: $(SRC_TOP)Headers/datafile.h
brltest.$O: $(SRC_TOP)Headers/options.h
brltest.$O: $(SRC_TOP)Headers/strfmth.h
brltest.$O: $(SRC_TOP)Headers/variables.h
brltest.$O: $(SRC_DIR)/parameters.h
brltest.$O: $(SRC_TOP)Headers/log.h
brltest.$O: $(SRC_TOP)Headers/parse.h
brltest.$O: $(SRC_TOP)Headers/file.h
brltest.$O: $(SRC_TOP)Headers/get_sockets.h
brltest.$O: $(SRC_TOP)Headers/ktb_types.h
brltest.$O: $(SRC_DIR)/cmd_queue.h
brltest.$O: $(SRC_TOP)Headers/api_types.h
brltest.$O: $(SRC_TOP)Headers/async.h
brltest.$O: $(SRC_TOP)Headers/brl_types.h
brltest.$O: $(SRC_TOP)Headers/driver.h
brltest.$O: $(SRC_TOP)Headers/gio_types.h
brltest.$O: $(SRC_TOP)Headers/queue.h
brltest.$O: $(SRC_TOP)Headers/serial_types.h
brltest.$O: $(SRC_TOP)Headers/usb_types.h
brltest.$O: $(SRC_DIR)/brl.h
brltest.$O: $(SRC_DIR)/brl_input.h
brltest.$O: $(SRC_TOP)Headers/brl_utils.h
brltest.$O: $(SRC_TOP)Headers/ttb.h
brltest.$O: $(SRC_TOP)Headers/ktb.h
brltest.$O: $(SRC_TOP)Headers/message.h
brltest.$O: $(SRC_TOP)Headers/charset.h
brltest.$O: $(SRC_TOP)Headers/lock.h
brltest.$O: $(SRC_TOP)Headers/async_wait.h
brltest.$O: $(SRC_DIR)/learn.h
brltest.$O: $(SRC_TOP)Headers/scr_types.h
brltest.$O: $(SRC_DIR)/scr.h
brltest.$O: $(SRC_TOP)Headers/alert.h

# Dependencies for brltty-atb.$O:
brltty-atb.$O: $(SRC_DIR)/brltty-atb.c
brltty-atb.$O: $(SRC_TOP)Headers/prologue.h
brltty-atb.$O: $(BLD_TOP)config.h
brltty-atb.$O: $(BLD_TOP)forbuild.h
brltty-atb.$O: $(SRC_TOP)Headers/pid.h
brltty-atb.$O: $(SRC_TOP)Headers/program.h
brltty-atb.$O: $(SRC_TOP)Headers/datafile.h
brltty-atb.$O: $(SRC_TOP)Headers/options.h
brltty-atb.$O: $(SRC_TOP)Headers/strfmth.h
brltty-atb.$O: $(SRC_TOP)Headers/variables.h
brltty-atb.$O: $(SRC_TOP)Headers/log.h
brltty-atb.$O: $(SRC_TOP)Headers/atb.h

# Dependencies for brltty-cldr.$O:
brltty-cldr.$O: $(SRC_DIR)/brltty-cldr.c
brltty-cldr.$O: $(SRC_TOP)Headers/prologue.h
brltty-cldr.$O: $(BLD_TOP)config.h
brltty-cldr.$O: $(BLD_TOP)forbuild.h
brltty-cldr.$O: $(SRC_TOP)Headers/pid.h
brltty-cldr.$O: $(SRC_TOP)Headers/program.h
brltty-cldr.$O: $(SRC_TOP)Headers/datafile.h
brltty-cldr.$O: $(SRC_TOP)Headers/options.h
brltty-cldr.$O: $(SRC_TOP)Headers/strfmth.h
brltty-cldr.$O: $(SRC_TOP)Headers/variables.h
brltty-cldr.$O: $(SRC_TOP)Headers/log.h
brltty-cldr.$O: $(SRC_TOP)Headers/cldr.h
brltty-cldr.$O: $(SRC_TOP)Headers/charset.h
brltty-cldr.$O: $(SRC_TOP)Headers/lock.h

# Dependencies for brltty-ctb.$O:
brltty-ctb.$O: $(SRC_DIR)/brltty-ctb.c
brltty-ctb.$O: $(SRC_TOP)Headers/prologue.h
brltty-ctb.$O: $(BLD_TOP)config.h
brltty-ctb.$O: $(BLD_TOP)forbuild.h
brltty-ctb.$O: $(SRC_TOP)Headers/pid.h
brltty-ctb.$O: $(SRC_TOP)Headers/program.h
brltty-ctb.$O: $(SRC_TOP)Headers/datafile.h
brltty-ctb.$O: $(SRC_TOP)Headers/options.h
brltty-ctb.$O: $(SRC_TOP)Headers/strfmth.h
brltty-ctb.$O: $(SRC_TOP)Headers/variables.h
brltty-ctb.$O: $(SRC_TOP)Headers/prefs.h
brltty-ctb.$O: $(SRC_TOP)Headers/log.h
brltty-ctb.$O: $(SRC_TOP)Headers/file.h
brltty-ctb.$O: $(SRC_TOP)Headers/get_sockets.h
brltty-ctb.$O: $(SRC_TOP)Headers/parse.h
brltty-ctb.$O: $(SRC_TOP)Headers/charset.h
brltty-ctb.$O: $(SRC_TOP)Headers/lock.h
brltty-ctb.$O: $(SRC_TOP)Headers/unicode.h
brltty-ctb.$O: $(SRC_TOP)Headers/ascii.h
brltty-ctb.$O: $(SRC_TOP)Headers/ttb.h
brltty-ctb.$O: $(SRC_TOP)Headers/ctb.h
brltty-ctb.$O: $(SRC_TOP)Headers/ctb_types.h

# Dependencies for brltty-ktb.$O:
brltty-ktb.$O: $(SRC_DIR)/brltty-ktb.c
brltty-ktb.$O: $(SRC_TOP)Headers/prologue.h
brltty-ktb.$O: $(BLD_TOP)config.h
brltty-ktb.$O: $(BLD_TOP)forbuild.h
brltty-ktb.$O: $(SRC_TOP)Headers/pid.h
brltty-ktb.$O: $(SRC_TOP)Headers/program.h
brltty-ktb.$O: $(SRC_TOP)Headers/datafile.h
brltty-ktb.$O: $(SRC_TOP)Headers/options.h
brltty-ktb.$O: $(SRC_TOP)Headers/strfmth.h
brltty-ktb.$O: $(SRC_TOP)Headers/variables.h
brltty-ktb.$O: $(SRC_TOP)Headers/log.h
brltty-ktb.$O: $(SRC_TOP)Headers/file.h
brltty-ktb.$O: $(SRC_TOP)Headers/get_sockets.h
brltty-ktb.$O: $(SRC_TOP)Headers/parse.h
brltty-ktb.$O: $(SRC_TOP)Headers/dynld.h
brltty-ktb.$O: $(SRC_TOP)Headers/ktb.h
brltty-ktb.$O: $(SRC_TOP)Headers/ktb_types.h
brltty-ktb.$O: $(SRC_DIR)/ktb_keyboard.h
brltty-ktb.$O: $(SRC_TOP)Headers/api_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/async.h
brltty-ktb.$O: $(SRC_TOP)Headers/brl_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/driver.h
brltty-ktb.$O: $(SRC_TOP)Headers/gio_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/queue.h
brltty-ktb.$O: $(SRC_TOP)Headers/serial_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/usb_types.h
brltty-ktb.$O: $(SRC_DIR)/brl.h
brltty-ktb.$O: $(SRC_TOP)Headers/cmd.h
brltty-ktb.$O: $(SRC_TOP)Headers/cmd_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/ctb.h
brltty-ktb.$O: $(SRC_TOP)Headers/ctb_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/prefs.h
brltty-ktb.$O: $(SRC_TOP)Headers/scr_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/spk.h
brltty-ktb.$O: $(SRC_TOP)Headers/spk_types.h
brltty-ktb.$O: $(SRC_TOP)Headers/timing.h
brltty-ktb.$O: $(SRC_DIR)/core.h
brltty-ktb.$O: $(SRC_DIR)/profile_types.h
brltty-ktb.$O: $(SRC_DIR)/ses.h
brltty-ktb.$O: $(SRC_DIR)/scr.h
brltty-ktb.$O: $(SRC_TOP)Headers/message.h
brltty-ktb.$O: $(SRC_DIR)/update.h
brltty-ktb.$O: $(SRC_TOP)Headers/alert.h

# Dependencies for brltty-lscmds.$O:
brltty-lscmds.$O: $(SRC_DIR)/brltty-lscmds.c
brltty-lscmds.$O: $(SRC_TOP)Headers/prologue.h
brltty-lscmds.$O: $(BLD_TOP)config.h
brltty-lscmds.$O: $(BLD_TOP)forbuild.h
brltty-lscmds.$O: $(SRC_TOP)Headers/pid.h
brltty-lscmds.$O: $(SRC_TOP)Headers/program.h
brltty-lscmds.$O: $(SRC_TOP)Headers/datafile.h
brltty-lscmds.$O: $(SRC_TOP)Headers/options.h
brltty-lscmds.$O: $(SRC_TOP)Headers/strfmth.h
brltty-lscmds.$O: $(SRC_TOP)Headers/variables.h
brltty-lscmds.$O: $(SRC_DIR)/ktb_cmds.h
brltty-lscmds.$O: $(SRC_TOP)Headers/cmd.h
brltty-lscmds.$O: $(SRC_TOP)Headers/cmd_types.h

# Dependencies for brltty-lsinc.$O:
brltty-lsinc.$O: $(SRC_DIR)/brltty-lsinc.c
brltty-lsinc.$O: $(SRC_TOP)Headers/prologue.h
brltty-lsinc.$O: $(BLD_TOP)config.h
brltty-lsinc.$O: $(BLD_TOP)forbuild.h
brltty-lsinc.$O: $(SRC_TOP)Headers/log.h
brltty-lsinc.$O: $(SRC_TOP)Headers/pid.h
brltty-lsinc.$O: $(SRC_TOP)Headers/program.h
brltty-lsinc.$O: $(SRC_TOP)Headers/datafile.h
brltty-lsinc.$O: $(SRC_TOP)Headers/options.h
brltty-lsinc.$O: $(SRC_TOP)Headers/strfmth.h
brltty-lsinc.$O: $(SRC_TOP)Headers/variables.h
brltty-lsinc.$O: $(SRC_TOP)Headers/file.h
brltty-lsinc.$O: $(SRC_TOP)Headers/get_sockets.h

# Dependencies for brltty-morse.$O:
brltty-morse.$O: $(SRC_DIR)/brltty-morse.c
brltty-morse.$O: $(SRC_TOP)Headers/prologue.h
brltty-morse.$O: $(BLD_TOP)config.h
brltty-morse.$O: $(BLD_TOP)forbuild.h
brltty-morse.$O: $(SRC_TOP)Headers/log.h
brltty-morse.$O: $(SRC_TOP)Headers/datafile.h
brltty-morse.$O: $(SRC_TOP)Headers/options.h
brltty-morse.$O: $(SRC_TOP)Headers/pid.h
brltty-morse.$O: $(SRC_TOP)Headers/program.h
brltty-morse.$O: $(SRC_TOP)Headers/strfmth.h
brltty-morse.$O: $(SRC_TOP)Headers/variables.h
brltty-morse.$O: $(SRC_TOP)Headers/prefs.h
brltty-morse.$O: $(SRC_TOP)Headers/parse.h
brltty-morse.$O: $(SRC_TOP)Headers/tune_types.h
brltty-morse.$O: $(SRC_TOP)Headers/tune_utils.h
brltty-morse.$O: $(SRC_TOP)Headers/note_types.h
brltty-morse.$O: $(SRC_TOP)Headers/notes.h
brltty-morse.$O: $(SRC_TOP)Headers/morse.h
brltty-morse.$O: $(SRC_TOP)Headers/alert.h

# Dependencies for brltty-trtxt.$O:
brltty-trtxt.$O: $(SRC_DIR)/brltty-trtxt.c
brltty-trtxt.$O: $(SRC_TOP)Headers/prologue.h
brltty-trtxt.$O: $(BLD_TOP)config.h
brltty-trtxt.$O: $(BLD_TOP)forbuild.h
brltty-trtxt.$O: $(SRC_TOP)Headers/pid.h
brltty-trtxt.$O: $(SRC_TOP)Headers/program.h
brltty-trtxt.$O: $(SRC_TOP)Headers/datafile.h
brltty-trtxt.$O: $(SRC_TOP)Headers/options.h
brltty-trtxt.$O: $(SRC_TOP)Headers/strfmth.h
brltty-trtxt.$O: $(SRC_TOP)Headers/variables.h
brltty-trtxt.$O: $(SRC_TOP)Headers/log.h
brltty-trtxt.$O: $(SRC_TOP)Headers/file.h
brltty-trtxt.$O: $(SRC_TOP)Headers/get_sockets.h
brltty-trtxt.$O: $(SRC_TOP)Headers/unicode.h
brltty-trtxt.$O: $(SRC_TOP)Headers/charset.h
brltty-trtxt.$O: $(SRC_TOP)Headers/lock.h
brltty-trtxt.$O: $(SRC_TOP)Headers/brl_dots.h
brltty-trtxt.$O: $(SRC_TOP)Headers/ttb.h

# Dependencies for brltty-ttb.$O:
brltty-ttb.$O: $(SRC_DIR)/brltty-ttb.c
brltty-ttb.$O: $(SRC_TOP)Headers/prologue.h
brltty-ttb.$O: $(BLD_TOP)config.h
brltty-ttb.$O: $(BLD_TOP)forbuild.h
brltty-ttb.$O: $(SRC_TOP)Headers/log.h
brltty-ttb.$O: $(SRC_TOP)Headers/strfmt.h
brltty-ttb.$O: $(SRC_TOP)Headers/strfmth.h
brltty-ttb.$O: $(SRC_TOP)Headers/pid.h
brltty-ttb.$O: $(SRC_TOP)Headers/program.h
brltty-ttb.$O: $(SRC_TOP)Headers/datafile.h
brltty-ttb.$O: $(SRC_TOP)Headers/options.h
brltty-ttb.$O: $(SRC_TOP)Headers/variables.h
brltty-ttb.$O: $(SRC_TOP)Headers/file.h
brltty-ttb.$O: $(SRC_TOP)Headers/get_sockets.h
brltty-ttb.$O: $(SRC_TOP)Headers/get_select.h
brltty-ttb.$O: $(SRC_TOP)Headers/brl_dots.h
brltty-ttb.$O: $(SRC_TOP)Headers/charset.h
brltty-ttb.$O: $(SRC_TOP)Headers/lock.h
brltty-ttb.$O: $(SRC_TOP)Headers/ttb.h
brltty-ttb.$O: $(SRC_TOP)Headers/bitmask.h
brltty-ttb.$O: $(SRC_TOP)Headers/dataarea.h
brltty-ttb.$O: $(SRC_TOP)Headers/unicode.h
brltty-ttb.$O: $(SRC_DIR)/ttb_internal.h
brltty-ttb.$O: $(SRC_DIR)/ttb_compile.h
brltty-ttb.$O: $(SRC_TOP)Headers/get_curses.h
brltty-ttb.$O: brlapi.h
brltty-ttb.$O: brlapi_constants.h
brltty-ttb.$O: $(SRC_DIR)/brlapi_keycodes.h

# Dependencies for brltty-tune.$O:
brltty-tune.$O: $(SRC_DIR)/brltty-tune.c
brltty-tune.$O: $(SRC_TOP)Headers/prologue.h
brltty-tune.$O: $(BLD_TOP)config.h
brltty-tune.$O: $(BLD_TOP)forbuild.h
brltty-tune.$O: $(SRC_TOP)Headers/log.h
brltty-tune.$O: $(SRC_TOP)Headers/datafile.h
brltty-tune.$O: $(SRC_TOP)Headers/options.h
brltty-tune.$O: $(SRC_TOP)Headers/pid.h
brltty-tune.$O: $(SRC_TOP)Headers/program.h
brltty-tune.$O: $(SRC_TOP)Headers/strfmth.h
brltty-tune.$O: $(SRC_TOP)Headers/variables.h
brltty-tune.$O: $(SRC_TOP)Headers/prefs.h
brltty-tune.$O: $(SRC_TOP)Headers/tune_types.h
brltty-tune.$O: $(SRC_TOP)Headers/tune_utils.h
brltty-tune.$O: $(SRC_TOP)Headers/note_types.h
brltty-tune.$O: $(SRC_TOP)Headers/tune.h
brltty-tune.$O: $(SRC_TOP)Headers/tune_build.h
brltty-tune.$O: $(SRC_TOP)Headers/notes.h
brltty-tune.$O: $(SRC_TOP)Headers/alert.h

# Dependencies for brltty.$O:
brltty.$O: $(SRC_DIR)/brltty.c
brltty.$O: $(SRC_TOP)Headers/prologue.h
brltty.$O: $(BLD_TOP)config.h
brltty.$O: $(BLD_TOP)forbuild.h
brltty.$O: $(SRC_TOP)Headers/embed.h
brltty.$O: $(SRC_TOP)Headers/pid.h
brltty.$O: $(SRC_TOP)Headers/program.h
brltty.$O: $(SRC_TOP)Headers/log.h
brltty.$O: $(SRC_TOP)Headers/api_types.h
brltty.$O: $(SRC_TOP)Headers/ktb_types.h
brltty.$O: $(SRC_DIR)/api_control.h
brltty.$O: $(SRC_TOP)Headers/async.h
brltty.$O: $(SRC_TOP)Headers/brl_types.h
brltty.$O: $(SRC_TOP)Headers/cmd.h
brltty.$O: $(SRC_TOP)Headers/cmd_types.h
brltty.$O: $(SRC_TOP)Headers/ctb.h
brltty.$O: $(SRC_TOP)Headers/ctb_types.h
brltty.$O: $(SRC_TOP)Headers/driver.h
brltty.$O: $(SRC_TOP)Headers/gio_types.h
brltty.$O: $(SRC_TOP)Headers/ktb.h
brltty.$O: $(SRC_TOP)Headers/prefs.h
brltty.$O: $(SRC_TOP)Headers/queue.h
brltty.$O: $(SRC_TOP)Headers/scr_types.h
brltty.$O: $(SRC_TOP)Headers/serial_types.h
brltty.$O: $(SRC_TOP)Headers/spk.h
brltty.$O: $(SRC_TOP)Headers/spk_types.h
brltty.$O: $(SRC_TOP)Headers/strfmth.h
brltty.$O: $(SRC_TOP)Headers/timing.h
brltty.$O: $(SRC_TOP)Headers/usb_types.h
brltty.$O: $(SRC_DIR)/brl.h
brltty.$O: $(SRC_DIR)/core.h
brltty.$O: $(SRC_DIR)/profile_types.h
brltty.$O: $(SRC_DIR)/ses.h

# Dependencies for brltty_jni.$O:
brltty_jni.$O: $(SRC_DIR)/brltty_jni.c
brltty_jni.$O: $(SRC_TOP)Headers/prologue.h
brltty_jni.$O: $(BLD_TOP)config.h
brltty_jni.$O: $(BLD_TOP)forbuild.h
brltty_jni.$O: $(SRC_TOP)Headers/embed.h
brltty_jni.$O: $(SRC_TOP)Headers/pid.h
brltty_jni.$O: $(SRC_TOP)Headers/program.h
brltty_jni.$O: $(SRC_TOP)Headers/common_java.h
brltty_jni.$O: $(SRC_TOP)Headers/system_java.h

# Dependencies for charset.$O:
charset.$O: $(SRC_DIR)/charset.c
charset.$O: $(SRC_TOP)Headers/prologue.h
charset.$O: $(BLD_TOP)config.h
charset.$O: $(BLD_TOP)forbuild.h
charset.$O: $(SRC_TOP)Headers/log.h
charset.$O: $(SRC_TOP)Headers/charset.h
charset.$O: $(SRC_TOP)Headers/lock.h
charset.$O: $(SRC_DIR)/charset_internal.h
charset.$O: $(SRC_TOP)Headers/unicode.h
charset.$O: $(SRC_TOP)Headers/pid.h
charset.$O: $(SRC_TOP)Headers/program.h
charset.$O: $(SRC_TOP)Headers/system_windows.h
charset.$O: $(SRC_TOP)Headers/common_java.h
charset.$O: $(SRC_TOP)Headers/system_java.h

# Dependencies for charset_grub.$O:
charset_grub.$O: $(SRC_DIR)/charset_grub.c
charset_grub.$O: $(SRC_TOP)Headers/prologue.h
charset_grub.$O: $(BLD_TOP)config.h
charset_grub.$O: $(BLD_TOP)forbuild.h
charset_grub.$O: $(SRC_TOP)Headers/charset.h
charset_grub.$O: $(SRC_TOP)Headers/lock.h
charset_grub.$O: $(SRC_DIR)/charset_internal.h

# Dependencies for charset_iconv.$O:
charset_iconv.$O: $(SRC_DIR)/charset_iconv.c
charset_iconv.$O: $(SRC_TOP)Headers/prologue.h
charset_iconv.$O: $(BLD_TOP)config.h
charset_iconv.$O: $(BLD_TOP)forbuild.h
charset_iconv.$O: $(SRC_TOP)Headers/log.h
charset_iconv.$O: $(SRC_TOP)Headers/charset.h
charset_iconv.$O: $(SRC_TOP)Headers/lock.h
charset_iconv.$O: $(SRC_DIR)/charset_internal.h
charset_iconv.$O: $(SRC_TOP)Headers/pid.h
charset_iconv.$O: $(SRC_TOP)Headers/program.h

# Dependencies for charset_msdos.$O:
charset_msdos.$O: $(SRC_DIR)/charset_msdos.c
charset_msdos.$O: $(SRC_TOP)Headers/prologue.h
charset_msdos.$O: $(BLD_TOP)config.h
charset_msdos.$O: $(BLD_TOP)forbuild.h
charset_msdos.$O: $(SRC_TOP)Headers/charset.h
charset_msdos.$O: $(SRC_TOP)Headers/lock.h
charset_msdos.$O: $(SRC_DIR)/charset_internal.h
charset_msdos.$O: $(SRC_TOP)Headers/unicode.h
charset_msdos.$O: $(SRC_TOP)Headers/system_msdos.h

# Dependencies for charset_none.$O:
charset_none.$O: $(SRC_DIR)/charset_none.c
charset_none.$O: $(SRC_TOP)Headers/prologue.h
charset_none.$O: $(BLD_TOP)config.h
charset_none.$O: $(BLD_TOP)forbuild.h
charset_none.$O: $(SRC_TOP)Headers/charset.h
charset_none.$O: $(SRC_TOP)Headers/lock.h
charset_none.$O: $(SRC_DIR)/charset_internal.h

# Dependencies for charset_windows.$O:
charset_windows.$O: $(SRC_DIR)/charset_windows.c
charset_windows.$O: $(SRC_TOP)Headers/prologue.h
charset_windows.$O: $(BLD_TOP)config.h
charset_windows.$O: $(BLD_TOP)forbuild.h
charset_windows.$O: $(SRC_TOP)Headers/log.h
charset_windows.$O: $(SRC_TOP)Headers/charset.h
charset_windows.$O: $(SRC_TOP)Headers/lock.h
charset_windows.$O: $(SRC_DIR)/charset_internal.h

# Dependencies for cldr.$O:
cldr.$O: $(SRC_DIR)/cldr.c
cldr.$O: $(SRC_TOP)Headers/prologue.h
cldr.$O: $(BLD_TOP)config.h
cldr.$O: $(BLD_TOP)forbuild.h
cldr.$O: $(SRC_TOP)Headers/log.h
cldr.$O: $(SRC_TOP)Headers/cldr.h
cldr.$O: $(SRC_TOP)Headers/file.h
cldr.$O: $(SRC_TOP)Headers/get_sockets.h

# Dependencies for cmd.$O:
cmd.$O: $(SRC_DIR)/cmd.c
cmd.$O: $(SRC_TOP)Headers/prologue.h
cmd.$O: $(BLD_TOP)config.h
cmd.$O: $(BLD_TOP)forbuild.h
cmd.$O: $(SRC_TOP)Headers/log.h
cmd.$O: $(SRC_TOP)Headers/strfmt.h
cmd.$O: $(SRC_TOP)Headers/strfmth.h
cmd.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd.$O: $(SRC_TOP)Headers/brl_dots.h
cmd.$O: $(SRC_TOP)Headers/brl_custom.h
cmd.$O: $(SRC_TOP)Headers/cmd.h
cmd.$O: $(SRC_TOP)Headers/cmd_types.h
cmd.$O: $(SRC_TOP)Headers/pid.h
cmd.$O: $(SRC_TOP)Headers/program.h
cmd.$O: cmds.auto.h

# Dependencies for cmd_brlapi.$O:
cmd_brlapi.$O: $(SRC_DIR)/cmd_brlapi.c
cmd_brlapi.$O: $(SRC_TOP)Headers/prologue.h
cmd_brlapi.$O: $(BLD_TOP)config.h
cmd_brlapi.$O: $(BLD_TOP)forbuild.h
cmd_brlapi.$O: brlapi_constants.h
cmd_brlapi.$O: $(SRC_DIR)/brlapi_keycodes.h
cmd_brlapi.$O: $(SRC_DIR)/cmd_brlapi.h
cmd_brlapi.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_brlapi.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_brlapi.$O: $(SRC_TOP)Headers/ttb.h

# Dependencies for cmd_clipboard.$O:
cmd_clipboard.$O: $(SRC_DIR)/cmd_clipboard.c
cmd_clipboard.$O: $(SRC_TOP)Headers/prologue.h
cmd_clipboard.$O: $(BLD_TOP)config.h
cmd_clipboard.$O: $(BLD_TOP)forbuild.h
cmd_clipboard.$O: $(SRC_TOP)Headers/log.h
cmd_clipboard.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_clipboard.$O: $(SRC_DIR)/cmd_queue.h
cmd_clipboard.$O: $(SRC_DIR)/cmd_clipboard.h
cmd_clipboard.$O: $(SRC_TOP)Headers/strfmth.h
cmd_clipboard.$O: $(SRC_DIR)/cmd_utils.h
cmd_clipboard.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_clipboard.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_clipboard.$O: $(SRC_TOP)Headers/driver.h
cmd_clipboard.$O: $(SRC_TOP)Headers/scr_types.h
cmd_clipboard.$O: $(SRC_DIR)/scr.h
cmd_clipboard.$O: $(SRC_DIR)/routing.h
cmd_clipboard.$O: $(SRC_TOP)Headers/alert.h
cmd_clipboard.$O: $(SRC_TOP)Headers/queue.h
cmd_clipboard.$O: $(SRC_TOP)Headers/file.h
cmd_clipboard.$O: $(SRC_TOP)Headers/get_sockets.h
cmd_clipboard.$O: $(SRC_TOP)Headers/datafile.h
cmd_clipboard.$O: $(SRC_TOP)Headers/variables.h
cmd_clipboard.$O: $(SRC_TOP)Headers/charset.h
cmd_clipboard.$O: $(SRC_TOP)Headers/lock.h
cmd_clipboard.$O: $(SRC_TOP)Headers/api_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/async.h
cmd_clipboard.$O: $(SRC_TOP)Headers/brl_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/cmd.h
cmd_clipboard.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/ctb.h
cmd_clipboard.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/gio_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/ktb.h
cmd_clipboard.$O: $(SRC_TOP)Headers/pid.h
cmd_clipboard.$O: $(SRC_TOP)Headers/prefs.h
cmd_clipboard.$O: $(SRC_TOP)Headers/program.h
cmd_clipboard.$O: $(SRC_TOP)Headers/serial_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/spk.h
cmd_clipboard.$O: $(SRC_TOP)Headers/spk_types.h
cmd_clipboard.$O: $(SRC_TOP)Headers/timing.h
cmd_clipboard.$O: $(SRC_TOP)Headers/usb_types.h
cmd_clipboard.$O: $(SRC_DIR)/brl.h
cmd_clipboard.$O: $(SRC_DIR)/core.h
cmd_clipboard.$O: $(SRC_DIR)/profile_types.h
cmd_clipboard.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_custom.$O:
cmd_custom.$O: $(SRC_DIR)/cmd_custom.c
cmd_custom.$O: $(SRC_TOP)Headers/prologue.h
cmd_custom.$O: $(BLD_TOP)config.h
cmd_custom.$O: $(BLD_TOP)forbuild.h
cmd_custom.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_custom.$O: $(SRC_DIR)/cmd_queue.h
cmd_custom.$O: $(SRC_DIR)/cmd_custom.h
cmd_custom.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_custom.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_custom.$O: $(SRC_TOP)Headers/brl_custom.h

# Dependencies for cmd_input.$O:
cmd_input.$O: $(SRC_DIR)/cmd_input.c
cmd_input.$O: $(SRC_TOP)Headers/prologue.h
cmd_input.$O: $(BLD_TOP)config.h
cmd_input.$O: $(BLD_TOP)forbuild.h
cmd_input.$O: $(SRC_TOP)Headers/log.h
cmd_input.$O: $(SRC_TOP)Headers/report.h
cmd_input.$O: $(SRC_TOP)Headers/alert.h
cmd_input.$O: $(SRC_DIR)/parameters.h
cmd_input.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_input.$O: $(SRC_DIR)/cmd_queue.h
cmd_input.$O: $(SRC_DIR)/cmd_input.h
cmd_input.$O: $(SRC_TOP)Headers/strfmth.h
cmd_input.$O: $(SRC_DIR)/cmd_utils.h
cmd_input.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_input.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_input.$O: $(SRC_TOP)Headers/unicode.h
cmd_input.$O: $(SRC_TOP)Headers/ttb.h
cmd_input.$O: $(SRC_TOP)Headers/driver.h
cmd_input.$O: $(SRC_TOP)Headers/scr_types.h
cmd_input.$O: $(SRC_DIR)/scr.h
cmd_input.$O: $(SRC_TOP)Headers/async.h
cmd_input.$O: $(SRC_TOP)Headers/async_alarm.h
cmd_input.$O: $(SRC_TOP)Headers/timing.h
cmd_input.$O: $(SRC_TOP)Headers/api_types.h
cmd_input.$O: $(SRC_TOP)Headers/brl_types.h
cmd_input.$O: $(SRC_TOP)Headers/cmd.h
cmd_input.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_input.$O: $(SRC_TOP)Headers/ctb.h
cmd_input.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_input.$O: $(SRC_TOP)Headers/gio_types.h
cmd_input.$O: $(SRC_TOP)Headers/ktb.h
cmd_input.$O: $(SRC_TOP)Headers/pid.h
cmd_input.$O: $(SRC_TOP)Headers/prefs.h
cmd_input.$O: $(SRC_TOP)Headers/program.h
cmd_input.$O: $(SRC_TOP)Headers/queue.h
cmd_input.$O: $(SRC_TOP)Headers/serial_types.h
cmd_input.$O: $(SRC_TOP)Headers/spk.h
cmd_input.$O: $(SRC_TOP)Headers/spk_types.h
cmd_input.$O: $(SRC_TOP)Headers/usb_types.h
cmd_input.$O: $(SRC_DIR)/brl.h
cmd_input.$O: $(SRC_DIR)/core.h
cmd_input.$O: $(SRC_DIR)/profile_types.h
cmd_input.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_keycodes.$O:
cmd_keycodes.$O: $(SRC_DIR)/cmd_keycodes.c
cmd_keycodes.$O: $(SRC_TOP)Headers/prologue.h
cmd_keycodes.$O: $(BLD_TOP)config.h
cmd_keycodes.$O: $(BLD_TOP)forbuild.h
cmd_keycodes.$O: $(SRC_TOP)Headers/log.h
cmd_keycodes.$O: $(SRC_TOP)Headers/report.h
cmd_keycodes.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_keycodes.$O: $(SRC_DIR)/cmd_queue.h
cmd_keycodes.$O: $(SRC_DIR)/cmd_keycodes.h
cmd_keycodes.$O: $(SRC_TOP)Headers/kbd_keycodes.h
cmd_keycodes.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_keycodes.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_keycodes.$O: $(SRC_TOP)Headers/alert.h

# Dependencies for cmd_learn.$O:
cmd_learn.$O: $(SRC_DIR)/cmd_learn.c
cmd_learn.$O: $(SRC_TOP)Headers/prologue.h
cmd_learn.$O: $(BLD_TOP)config.h
cmd_learn.$O: $(BLD_TOP)forbuild.h
cmd_learn.$O: $(SRC_TOP)Headers/log.h
cmd_learn.$O: $(SRC_DIR)/parameters.h
cmd_learn.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_learn.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_learn.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_learn.$O: $(SRC_DIR)/cmd_queue.h
cmd_learn.$O: $(SRC_DIR)/cmd_learn.h
cmd_learn.$O: $(SRC_DIR)/learn.h
cmd_learn.$O: $(SRC_TOP)Headers/async.h
cmd_learn.$O: $(SRC_TOP)Headers/async_event.h
cmd_learn.$O: $(SRC_TOP)Headers/async_task.h
cmd_learn.$O: $(SRC_TOP)Headers/api_types.h
cmd_learn.$O: $(SRC_TOP)Headers/brl_types.h
cmd_learn.$O: $(SRC_TOP)Headers/driver.h
cmd_learn.$O: $(SRC_TOP)Headers/gio_types.h
cmd_learn.$O: $(SRC_TOP)Headers/queue.h
cmd_learn.$O: $(SRC_TOP)Headers/serial_types.h
cmd_learn.$O: $(SRC_TOP)Headers/usb_types.h
cmd_learn.$O: $(SRC_DIR)/update.h
cmd_learn.$O: $(SRC_TOP)Headers/message.h
cmd_learn.$O: $(SRC_TOP)Headers/cmd.h
cmd_learn.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_learn.$O: $(SRC_TOP)Headers/ctb.h
cmd_learn.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_learn.$O: $(SRC_TOP)Headers/ktb.h
cmd_learn.$O: $(SRC_TOP)Headers/pid.h
cmd_learn.$O: $(SRC_TOP)Headers/prefs.h
cmd_learn.$O: $(SRC_TOP)Headers/program.h
cmd_learn.$O: $(SRC_TOP)Headers/scr_types.h
cmd_learn.$O: $(SRC_TOP)Headers/spk.h
cmd_learn.$O: $(SRC_TOP)Headers/spk_types.h
cmd_learn.$O: $(SRC_TOP)Headers/strfmth.h
cmd_learn.$O: $(SRC_TOP)Headers/timing.h
cmd_learn.$O: $(SRC_DIR)/brl.h
cmd_learn.$O: $(SRC_DIR)/core.h
cmd_learn.$O: $(SRC_DIR)/profile_types.h
cmd_learn.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_miscellaneous.$O:
cmd_miscellaneous.$O: $(SRC_DIR)/cmd_miscellaneous.c
cmd_miscellaneous.$O: $(SRC_TOP)Headers/prologue.h
cmd_miscellaneous.$O: $(BLD_TOP)config.h
cmd_miscellaneous.$O: $(BLD_TOP)forbuild.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/strfmt.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/strfmth.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_miscellaneous.$O: $(SRC_DIR)/cmd_queue.h
cmd_miscellaneous.$O: $(SRC_DIR)/cmd_miscellaneous.h
cmd_miscellaneous.$O: $(SRC_DIR)/cmd_utils.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/prefs.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/driver.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/scr_base.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/scr_main.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/scr_types.h
cmd_miscellaneous.$O: $(SRC_DIR)/scr_internal.h
cmd_miscellaneous.$O: $(SRC_DIR)/scr_special.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/message.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/alert.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/api_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/async.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/brl_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/cmd.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/ctb.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/gio_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/ktb.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/pid.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/program.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/queue.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/serial_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/spk.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/spk_types.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/timing.h
cmd_miscellaneous.$O: $(SRC_TOP)Headers/usb_types.h
cmd_miscellaneous.$O: $(SRC_DIR)/brl.h
cmd_miscellaneous.$O: $(SRC_DIR)/core.h
cmd_miscellaneous.$O: $(SRC_DIR)/profile_types.h
cmd_miscellaneous.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_navigation.$O:
cmd_navigation.$O: $(SRC_DIR)/cmd_navigation.c
cmd_navigation.$O: $(SRC_TOP)Headers/prologue.h
cmd_navigation.$O: $(BLD_TOP)config.h
cmd_navigation.$O: $(BLD_TOP)forbuild.h
cmd_navigation.$O: $(SRC_TOP)Headers/log.h
cmd_navigation.$O: $(SRC_TOP)Headers/alert.h
cmd_navigation.$O: $(SRC_DIR)/parameters.h
cmd_navigation.$O: $(SRC_TOP)Headers/pid.h
cmd_navigation.$O: $(SRC_TOP)Headers/program.h
cmd_navigation.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_navigation.$O: $(SRC_DIR)/cmd_queue.h
cmd_navigation.$O: $(SRC_DIR)/cmd_navigation.h
cmd_navigation.$O: $(SRC_TOP)Headers/strfmth.h
cmd_navigation.$O: $(SRC_DIR)/cmd_utils.h
cmd_navigation.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_navigation.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_navigation.$O: $(SRC_TOP)Headers/parse.h
cmd_navigation.$O: $(SRC_TOP)Headers/rgx.h
cmd_navigation.$O: $(SRC_TOP)Headers/prefs.h
cmd_navigation.$O: $(SRC_DIR)/routing.h
cmd_navigation.$O: $(SRC_TOP)Headers/driver.h
cmd_navigation.$O: $(SRC_TOP)Headers/scr_types.h
cmd_navigation.$O: $(SRC_DIR)/scr.h
cmd_navigation.$O: $(SRC_TOP)Headers/api_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/async.h
cmd_navigation.$O: $(SRC_TOP)Headers/brl_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/cmd.h
cmd_navigation.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/ctb.h
cmd_navigation.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/gio_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/ktb.h
cmd_navigation.$O: $(SRC_TOP)Headers/queue.h
cmd_navigation.$O: $(SRC_TOP)Headers/serial_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/spk.h
cmd_navigation.$O: $(SRC_TOP)Headers/spk_types.h
cmd_navigation.$O: $(SRC_TOP)Headers/timing.h
cmd_navigation.$O: $(SRC_TOP)Headers/usb_types.h
cmd_navigation.$O: $(SRC_DIR)/brl.h
cmd_navigation.$O: $(SRC_DIR)/core.h
cmd_navigation.$O: $(SRC_DIR)/profile_types.h
cmd_navigation.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_preferences.$O:
cmd_preferences.$O: $(SRC_DIR)/cmd_preferences.c
cmd_preferences.$O: $(SRC_TOP)Headers/prologue.h
cmd_preferences.$O: $(BLD_TOP)config.h
cmd_preferences.$O: $(BLD_TOP)forbuild.h
cmd_preferences.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_preferences.$O: $(SRC_DIR)/cmd_queue.h
cmd_preferences.$O: $(SRC_DIR)/cmd_preferences.h
cmd_preferences.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_preferences.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_preferences.$O: $(SRC_TOP)Headers/prefs.h
cmd_preferences.$O: $(SRC_TOP)Headers/menu.h
cmd_preferences.$O: $(SRC_DIR)/menu_prefs.h
cmd_preferences.$O: $(SRC_TOP)Headers/driver.h
cmd_preferences.$O: $(SRC_TOP)Headers/scr_base.h
cmd_preferences.$O: $(SRC_TOP)Headers/scr_main.h
cmd_preferences.$O: $(SRC_TOP)Headers/scr_types.h
cmd_preferences.$O: $(SRC_DIR)/scr_internal.h
cmd_preferences.$O: $(SRC_DIR)/scr_special.h
cmd_preferences.$O: $(SRC_TOP)Headers/message.h
cmd_preferences.$O: $(SRC_TOP)Headers/alert.h
cmd_preferences.$O: $(SRC_TOP)Headers/api_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/async.h
cmd_preferences.$O: $(SRC_TOP)Headers/brl_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/cmd.h
cmd_preferences.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/ctb.h
cmd_preferences.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/gio_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/ktb.h
cmd_preferences.$O: $(SRC_TOP)Headers/pid.h
cmd_preferences.$O: $(SRC_TOP)Headers/program.h
cmd_preferences.$O: $(SRC_TOP)Headers/queue.h
cmd_preferences.$O: $(SRC_TOP)Headers/serial_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/spk.h
cmd_preferences.$O: $(SRC_TOP)Headers/spk_types.h
cmd_preferences.$O: $(SRC_TOP)Headers/strfmth.h
cmd_preferences.$O: $(SRC_TOP)Headers/timing.h
cmd_preferences.$O: $(SRC_TOP)Headers/usb_types.h
cmd_preferences.$O: $(SRC_DIR)/brl.h
cmd_preferences.$O: $(SRC_DIR)/core.h
cmd_preferences.$O: $(SRC_DIR)/profile_types.h
cmd_preferences.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_queue.$O:
cmd_queue.$O: $(SRC_DIR)/cmd_queue.c
cmd_queue.$O: $(SRC_TOP)Headers/prologue.h
cmd_queue.$O: $(BLD_TOP)config.h
cmd_queue.$O: $(BLD_TOP)forbuild.h
cmd_queue.$O: $(SRC_TOP)Headers/log.h
cmd_queue.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_queue.$O: $(SRC_DIR)/cmd_queue.h
cmd_queue.$O: $(SRC_TOP)Headers/cmd_enqueue.h
cmd_queue.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_queue.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_queue.$O: $(SRC_TOP)Headers/queue.h
cmd_queue.$O: $(SRC_TOP)Headers/async.h
cmd_queue.$O: $(SRC_TOP)Headers/async_alarm.h
cmd_queue.$O: $(SRC_TOP)Headers/timing.h
cmd_queue.$O: $(SRC_TOP)Headers/prefs.h
cmd_queue.$O: $(SRC_TOP)Headers/driver.h
cmd_queue.$O: $(SRC_TOP)Headers/scr_types.h
cmd_queue.$O: $(SRC_DIR)/scr.h
cmd_queue.$O: $(SRC_TOP)Headers/api_types.h
cmd_queue.$O: $(SRC_TOP)Headers/brl_types.h
cmd_queue.$O: $(SRC_TOP)Headers/cmd.h
cmd_queue.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_queue.$O: $(SRC_TOP)Headers/ctb.h
cmd_queue.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_queue.$O: $(SRC_TOP)Headers/gio_types.h
cmd_queue.$O: $(SRC_TOP)Headers/ktb.h
cmd_queue.$O: $(SRC_TOP)Headers/pid.h
cmd_queue.$O: $(SRC_TOP)Headers/program.h
cmd_queue.$O: $(SRC_TOP)Headers/serial_types.h
cmd_queue.$O: $(SRC_TOP)Headers/spk.h
cmd_queue.$O: $(SRC_TOP)Headers/spk_types.h
cmd_queue.$O: $(SRC_TOP)Headers/strfmth.h
cmd_queue.$O: $(SRC_TOP)Headers/usb_types.h
cmd_queue.$O: $(SRC_DIR)/brl.h
cmd_queue.$O: $(SRC_DIR)/core.h
cmd_queue.$O: $(SRC_DIR)/profile_types.h
cmd_queue.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_speech.$O:
cmd_speech.$O: $(SRC_DIR)/cmd_speech.c
cmd_speech.$O: $(SRC_TOP)Headers/prologue.h
cmd_speech.$O: $(BLD_TOP)config.h
cmd_speech.$O: $(BLD_TOP)forbuild.h
cmd_speech.$O: $(SRC_TOP)Headers/embed.h
cmd_speech.$O: $(SRC_TOP)Headers/pid.h
cmd_speech.$O: $(SRC_TOP)Headers/program.h
cmd_speech.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_speech.$O: $(SRC_DIR)/cmd_queue.h
cmd_speech.$O: $(SRC_DIR)/cmd_speech.h
cmd_speech.$O: $(SRC_TOP)Headers/strfmth.h
cmd_speech.$O: $(SRC_DIR)/cmd_utils.h
cmd_speech.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_speech.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_speech.$O: $(SRC_TOP)Headers/prefs.h
cmd_speech.$O: $(SRC_TOP)Headers/alert.h
cmd_speech.$O: $(SRC_TOP)Headers/driver.h
cmd_speech.$O: $(SRC_TOP)Headers/spk.h
cmd_speech.$O: $(SRC_TOP)Headers/spk_types.h
cmd_speech.$O: $(SRC_TOP)Headers/scr_types.h
cmd_speech.$O: $(SRC_DIR)/scr.h
cmd_speech.$O: $(SRC_TOP)Headers/api_types.h
cmd_speech.$O: $(SRC_TOP)Headers/async.h
cmd_speech.$O: $(SRC_TOP)Headers/brl_types.h
cmd_speech.$O: $(SRC_TOP)Headers/gio_types.h
cmd_speech.$O: $(SRC_TOP)Headers/queue.h
cmd_speech.$O: $(SRC_TOP)Headers/serial_types.h
cmd_speech.$O: $(SRC_TOP)Headers/usb_types.h
cmd_speech.$O: $(SRC_DIR)/update.h
cmd_speech.$O: $(SRC_TOP)Headers/cmd.h
cmd_speech.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_speech.$O: $(SRC_TOP)Headers/ctb.h
cmd_speech.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_speech.$O: $(SRC_TOP)Headers/ktb.h
cmd_speech.$O: $(SRC_TOP)Headers/timing.h
cmd_speech.$O: $(SRC_DIR)/brl.h
cmd_speech.$O: $(SRC_DIR)/core.h
cmd_speech.$O: $(SRC_DIR)/profile_types.h
cmd_speech.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_toggle.$O:
cmd_toggle.$O: $(SRC_DIR)/cmd_toggle.c
cmd_toggle.$O: $(SRC_TOP)Headers/prologue.h
cmd_toggle.$O: $(BLD_TOP)config.h
cmd_toggle.$O: $(BLD_TOP)forbuild.h
cmd_toggle.$O: $(SRC_DIR)/parameters.h
cmd_toggle.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_toggle.$O: $(SRC_DIR)/cmd_queue.h
cmd_toggle.$O: $(SRC_DIR)/cmd_toggle.h
cmd_toggle.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_toggle.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_toggle.$O: $(SRC_TOP)Headers/prefs.h
cmd_toggle.$O: $(SRC_TOP)Headers/driver.h
cmd_toggle.$O: $(SRC_TOP)Headers/scr_types.h
cmd_toggle.$O: $(SRC_DIR)/scr.h
cmd_toggle.$O: $(SRC_TOP)Headers/scr_base.h
cmd_toggle.$O: $(SRC_TOP)Headers/scr_main.h
cmd_toggle.$O: $(SRC_DIR)/scr_internal.h
cmd_toggle.$O: $(SRC_DIR)/scr_special.h
cmd_toggle.$O: $(SRC_TOP)Headers/alert.h
cmd_toggle.$O: $(SRC_TOP)Headers/note_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/tune.h
cmd_toggle.$O: $(SRC_TOP)Headers/tune_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/api_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/async.h
cmd_toggle.$O: $(SRC_TOP)Headers/brl_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/cmd.h
cmd_toggle.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/ctb.h
cmd_toggle.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/gio_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/ktb.h
cmd_toggle.$O: $(SRC_TOP)Headers/pid.h
cmd_toggle.$O: $(SRC_TOP)Headers/program.h
cmd_toggle.$O: $(SRC_TOP)Headers/queue.h
cmd_toggle.$O: $(SRC_TOP)Headers/serial_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/spk.h
cmd_toggle.$O: $(SRC_TOP)Headers/spk_types.h
cmd_toggle.$O: $(SRC_TOP)Headers/strfmth.h
cmd_toggle.$O: $(SRC_TOP)Headers/timing.h
cmd_toggle.$O: $(SRC_TOP)Headers/usb_types.h
cmd_toggle.$O: $(SRC_DIR)/brl.h
cmd_toggle.$O: $(SRC_DIR)/core.h
cmd_toggle.$O: $(SRC_DIR)/profile_types.h
cmd_toggle.$O: $(SRC_DIR)/ses.h

# Dependencies for cmd_touch.$O:
cmd_touch.$O: $(SRC_DIR)/cmd_touch.c
cmd_touch.$O: $(SRC_TOP)Headers/prologue.h
cmd_touch.$O: $(BLD_TOP)config.h
cmd_touch.$O: $(BLD_TOP)forbuild.h
cmd_touch.$O: $(SRC_TOP)Headers/log.h
cmd_touch.$O: $(SRC_TOP)Headers/strfmth.h
cmd_touch.$O: $(SRC_DIR)/cmd_utils.h
cmd_touch.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_touch.$O: $(SRC_DIR)/cmd_queue.h
cmd_touch.$O: $(SRC_DIR)/cmd_touch.h
cmd_touch.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_touch.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_touch.$O: $(SRC_TOP)Headers/api_types.h
cmd_touch.$O: $(SRC_TOP)Headers/async.h
cmd_touch.$O: $(SRC_TOP)Headers/brl_types.h
cmd_touch.$O: $(SRC_TOP)Headers/brl_utils.h
cmd_touch.$O: $(SRC_TOP)Headers/driver.h
cmd_touch.$O: $(SRC_TOP)Headers/gio_types.h
cmd_touch.$O: $(SRC_TOP)Headers/queue.h
cmd_touch.$O: $(SRC_TOP)Headers/serial_types.h
cmd_touch.$O: $(SRC_TOP)Headers/usb_types.h
cmd_touch.$O: $(SRC_TOP)Headers/report.h
cmd_touch.$O: $(SRC_TOP)Headers/bitmask.h
cmd_touch.$O: $(SRC_TOP)Headers/prefs.h

# Dependencies for cmd_utils.$O:
cmd_utils.$O: $(SRC_DIR)/cmd_utils.c
cmd_utils.$O: $(SRC_TOP)Headers/prologue.h
cmd_utils.$O: $(BLD_TOP)config.h
cmd_utils.$O: $(BLD_TOP)forbuild.h
cmd_utils.$O: $(SRC_TOP)Headers/strfmt.h
cmd_utils.$O: $(SRC_TOP)Headers/strfmth.h
cmd_utils.$O: $(SRC_TOP)Headers/alert.h
cmd_utils.$O: $(SRC_TOP)Headers/brl_cmds.h
cmd_utils.$O: $(SRC_TOP)Headers/brl_dots.h
cmd_utils.$O: $(SRC_TOP)Headers/unicode.h
cmd_utils.$O: $(SRC_TOP)Headers/driver.h
cmd_utils.$O: $(SRC_TOP)Headers/ktb_types.h
cmd_utils.$O: $(SRC_TOP)Headers/scr_types.h
cmd_utils.$O: $(SRC_DIR)/scr.h
cmd_utils.$O: $(SRC_TOP)Headers/api_types.h
cmd_utils.$O: $(SRC_TOP)Headers/async.h
cmd_utils.$O: $(SRC_TOP)Headers/brl_types.h
cmd_utils.$O: $(SRC_TOP)Headers/cmd.h
cmd_utils.$O: $(SRC_TOP)Headers/cmd_types.h
cmd_utils.$O: $(SRC_TOP)Headers/ctb.h
cmd_utils.$O: $(SRC_TOP)Headers/ctb_types.h
cmd_utils.$O: $(SRC_TOP)Headers/gio_types.h
cmd_utils.$O: $(SRC_TOP)Headers/ktb.h
cmd_utils.$O: $(SRC_TOP)Headers/pid.h
cmd_utils.$O: $(SRC_TOP)Headers/prefs.h
cmd_utils.$O: $(SRC_TOP)Headers/program.h
cmd_utils.$O: $(SRC_TOP)Headers/queue.h
cmd_utils.$O: $(SRC_TOP)Headers/serial_types.h
cmd_utils.$O: $(SRC_TOP)Headers/spk.h
cmd_utils.$O: $(SRC_TOP)Headers/spk_types.h
cmd_utils.$O: $(SRC_TOP)Headers/timing.h
cmd_utils.$O: $(SRC_TOP)Headers/usb_types.h
cmd_utils.$O: $(SRC_DIR)/brl.h
cmd_utils.$O: $(SRC_DIR)/core.h
cmd_utils.$O: $(SRC_DIR)/profile_types.h
cmd_utils.$O: $(SRC_DIR)/ses.h

# Dependencies for config.$O:
config.$O: $(SRC_DIR)/config.c
config.$O: $(SRC_TOP)Headers/prologue.h
config.$O: $(BLD_TOP)config.h
config.$O: $(BLD_TOP)forbuild.h
config.$O: $(SRC_DIR)/parameters.h
config.$O: $(SRC_TOP)Headers/embed.h
config.$O: $(SRC_TOP)Headers/pid.h
config.$O: $(SRC_TOP)Headers/program.h
config.$O: $(SRC_TOP)Headers/log.h
config.$O: $(SRC_TOP)Headers/report.h
config.$O: $(SRC_TOP)Headers/strfmt.h
config.$O: $(SRC_TOP)Headers/strfmth.h
config.$O: $(SRC_DIR)/activity.h
config.$O: $(SRC_TOP)Headers/api_types.h
config.$O: $(SRC_TOP)Headers/async.h
config.$O: $(SRC_TOP)Headers/brl_types.h
config.$O: $(SRC_TOP)Headers/driver.h
config.$O: $(SRC_TOP)Headers/gio_types.h
config.$O: $(SRC_TOP)Headers/ktb_types.h
config.$O: $(SRC_TOP)Headers/queue.h
config.$O: $(SRC_TOP)Headers/serial_types.h
config.$O: $(SRC_TOP)Headers/usb_types.h
config.$O: $(SRC_DIR)/update.h
config.$O: $(SRC_TOP)Headers/cmd.h
config.$O: $(SRC_TOP)Headers/cmd_types.h
config.$O: $(SRC_DIR)/cmd_navigation.h
config.$O: $(SRC_DIR)/brl.h
config.$O: $(SRC_TOP)Headers/brl_utils.h
config.$O: $(SRC_TOP)Headers/spk.h
config.$O: $(SRC_TOP)Headers/spk_types.h
config.$O: $(SRC_DIR)/spk_input.h
config.$O: $(SRC_TOP)Headers/scr_types.h
config.$O: $(SRC_DIR)/scr.h
config.$O: $(SRC_TOP)Headers/scr_base.h
config.$O: $(SRC_TOP)Headers/scr_main.h
config.$O: $(SRC_DIR)/scr_internal.h
config.$O: $(SRC_DIR)/scr_special.h
config.$O: $(SRC_TOP)Headers/status_types.h
config.$O: $(SRC_DIR)/status.h
config.$O: $(SRC_DIR)/blink.h
config.$O: $(SRC_TOP)Headers/variables.h
config.$O: $(SRC_TOP)Headers/datafile.h
config.$O: $(SRC_TOP)Headers/ttb.h
config.$O: $(SRC_TOP)Headers/atb.h
config.$O: $(SRC_TOP)Headers/ctb.h
config.$O: $(SRC_TOP)Headers/ctb_types.h
config.$O: $(SRC_TOP)Headers/ktb.h
config.$O: $(SRC_DIR)/ktb_keyboard.h
config.$O: $(SRC_DIR)/kbd.h
config.$O: $(SRC_TOP)Headers/alert.h
config.$O: $(SRC_TOP)Headers/bell.h
config.$O: $(SRC_TOP)Headers/leds.h
config.$O: $(SRC_TOP)Headers/note_types.h
config.$O: $(SRC_TOP)Headers/tune.h
config.$O: $(SRC_TOP)Headers/tune_types.h
config.$O: $(SRC_TOP)Headers/notes.h
config.$O: $(SRC_TOP)Headers/message.h
config.$O: $(SRC_TOP)Headers/file.h
config.$O: $(SRC_TOP)Headers/get_sockets.h
config.$O: $(SRC_TOP)Headers/parse.h
config.$O: $(SRC_TOP)Headers/dynld.h
config.$O: $(SRC_TOP)Headers/async_alarm.h
config.$O: $(SRC_TOP)Headers/timing.h
config.$O: $(SRC_TOP)Headers/revision.h
config.$O: $(SRC_TOP)Headers/service.h
config.$O: $(SRC_TOP)Headers/options.h
config.$O: $(SRC_DIR)/profile_types.h
config.$O: $(SRC_DIR)/brl_input.h
config.$O: $(SRC_DIR)/cmd_queue.h
config.$O: $(SRC_TOP)Headers/prefs.h
config.$O: $(SRC_DIR)/core.h
config.$O: $(SRC_DIR)/ses.h
config.$O: $(SRC_DIR)/api_control.h
config.$O: $(SRC_TOP)Headers/charset.h
config.$O: $(SRC_TOP)Headers/lock.h
config.$O: $(SRC_TOP)Headers/async_io.h
config.$O: $(SRC_TOP)Headers/io_generic.h
config.$O: $(SRC_TOP)Headers/io_usb.h
config.$O: $(SRC_TOP)Headers/io_bluetooth.h
config.$O: $(SRC_TOP)Headers/system_msdos.h

# Dependencies for core.$O:
core.$O: $(SRC_DIR)/core.c
core.$O: $(SRC_TOP)Headers/prologue.h
core.$O: $(BLD_TOP)config.h
core.$O: $(BLD_TOP)forbuild.h
core.$O: $(SRC_DIR)/parameters.h
core.$O: $(SRC_TOP)Headers/embed.h
core.$O: $(SRC_TOP)Headers/pid.h
core.$O: $(SRC_TOP)Headers/program.h
core.$O: $(SRC_TOP)Headers/log.h
core.$O: $(SRC_TOP)Headers/strfmt.h
core.$O: $(SRC_TOP)Headers/strfmth.h
core.$O: $(SRC_TOP)Headers/brl_cmds.h
core.$O: $(SRC_TOP)Headers/brl_dots.h
core.$O: $(SRC_TOP)Headers/ktb_types.h
core.$O: $(SRC_DIR)/cmd_queue.h
core.$O: $(SRC_DIR)/cmd_custom.h
core.$O: $(SRC_DIR)/cmd_navigation.h
core.$O: $(SRC_DIR)/cmd_input.h
core.$O: $(SRC_DIR)/cmd_keycodes.h
core.$O: $(SRC_DIR)/cmd_touch.h
core.$O: $(SRC_DIR)/cmd_toggle.h
core.$O: $(SRC_DIR)/cmd_preferences.h
core.$O: $(SRC_DIR)/cmd_clipboard.h
core.$O: $(SRC_DIR)/cmd_speech.h
core.$O: $(SRC_DIR)/cmd_learn.h
core.$O: $(SRC_DIR)/cmd_miscellaneous.h
core.$O: $(SRC_TOP)Headers/timing.h
core.$O: $(SRC_TOP)Headers/async.h
core.$O: $(SRC_TOP)Headers/async_wait.h
core.$O: $(SRC_TOP)Headers/async_event.h
core.$O: $(SRC_TOP)Headers/async_signal.h
core.$O: $(SRC_TOP)Headers/async_alarm.h
core.$O: $(SRC_TOP)Headers/alert.h
core.$O: $(SRC_TOP)Headers/ctb.h
core.$O: $(SRC_TOP)Headers/ctb_types.h
core.$O: $(SRC_DIR)/routing.h
core.$O: $(SRC_TOP)Headers/charset.h
core.$O: $(SRC_TOP)Headers/lock.h
core.$O: $(SRC_TOP)Headers/driver.h
core.$O: $(SRC_TOP)Headers/scr_types.h
core.$O: $(SRC_DIR)/scr.h
core.$O: $(SRC_TOP)Headers/api_types.h
core.$O: $(SRC_TOP)Headers/brl_types.h
core.$O: $(SRC_TOP)Headers/gio_types.h
core.$O: $(SRC_TOP)Headers/queue.h
core.$O: $(SRC_TOP)Headers/serial_types.h
core.$O: $(SRC_TOP)Headers/usb_types.h
core.$O: $(SRC_DIR)/update.h
core.$O: $(SRC_DIR)/ses.h
core.$O: $(SRC_DIR)/brl.h
core.$O: $(SRC_TOP)Headers/brl_utils.h
core.$O: $(SRC_TOP)Headers/prefs.h
core.$O: $(SRC_DIR)/api_control.h
core.$O: $(SRC_TOP)Headers/cmd.h
core.$O: $(SRC_TOP)Headers/cmd_types.h
core.$O: $(SRC_TOP)Headers/ktb.h
core.$O: $(SRC_TOP)Headers/spk.h
core.$O: $(SRC_TOP)Headers/spk_types.h
core.$O: $(SRC_DIR)/core.h
core.$O: $(SRC_DIR)/profile_types.h

# Dependencies for ctb_compile.$O:
ctb_compile.$O: $(SRC_DIR)/ctb_compile.c
ctb_compile.$O: $(SRC_TOP)Headers/prologue.h
ctb_compile.$O: $(BLD_TOP)config.h
ctb_compile.$O: $(BLD_TOP)forbuild.h
ctb_compile.$O: $(SRC_TOP)Headers/log.h
ctb_compile.$O: $(SRC_TOP)Headers/parse.h
ctb_compile.$O: $(SRC_TOP)Headers/file.h
ctb_compile.$O: $(SRC_TOP)Headers/get_sockets.h
ctb_compile.$O: $(SRC_TOP)Headers/ctb.h
ctb_compile.$O: $(SRC_TOP)Headers/ctb_types.h
ctb_compile.$O: $(SRC_DIR)/ctb_internal.h
ctb_compile.$O: $(SRC_TOP)Headers/datafile.h
ctb_compile.$O: $(SRC_TOP)Headers/variables.h
ctb_compile.$O: $(SRC_TOP)Headers/dataarea.h
ctb_compile.$O: $(SRC_TOP)Headers/brl_dots.h
ctb_compile.$O: $(SRC_TOP)Headers/cldr.h
ctb_compile.$O: $(SRC_TOP)Headers/unicode.h
ctb_compile.$O: $(SRC_TOP)Headers/charset.h
ctb_compile.$O: $(SRC_TOP)Headers/lock.h
ctb_compile.$O: $(SRC_TOP)Headers/hostcmd.h

# Dependencies for ctb_external.$O:
ctb_external.$O: $(SRC_DIR)/ctb_external.c
ctb_external.$O: $(SRC_TOP)Headers/prologue.h
ctb_external.$O: $(BLD_TOP)config.h
ctb_external.$O: $(BLD_TOP)forbuild.h
ctb_external.$O: $(SRC_TOP)Headers/log.h
ctb_external.$O: $(SRC_TOP)Headers/ctb.h
ctb_external.$O: $(SRC_TOP)Headers/ctb_types.h
ctb_external.$O: $(SRC_TOP)Headers/prefs.h
ctb_external.$O: $(SRC_DIR)/ctb_internal.h
ctb_external.$O: $(SRC_DIR)/ctb_translate.h
ctb_external.$O: $(SRC_TOP)Headers/brl_dots.h
ctb_external.$O: $(SRC_TOP)Headers/file.h
ctb_external.$O: $(SRC_TOP)Headers/get_sockets.h
ctb_external.$O: $(SRC_TOP)Headers/parse.h
ctb_external.$O: $(SRC_TOP)Headers/charset.h
ctb_external.$O: $(SRC_TOP)Headers/lock.h

# Dependencies for ctb_louis.$O:
ctb_louis.$O: $(SRC_DIR)/ctb_louis.c
ctb_louis.$O: $(SRC_TOP)Headers/prologue.h
ctb_louis.$O: $(BLD_TOP)config.h
ctb_louis.$O: $(BLD_TOP)forbuild.h
ctb_louis.$O: $(SRC_TOP)Headers/log.h
ctb_louis.$O: $(SRC_TOP)Headers/ctb.h
ctb_louis.$O: $(SRC_TOP)Headers/ctb_types.h
ctb_louis.$O: $(SRC_TOP)Headers/prefs.h
ctb_louis.$O: $(SRC_DIR)/ctb_internal.h
ctb_louis.$O: $(SRC_DIR)/ctb_translate.h

# Dependencies for ctb_native.$O:
ctb_native.$O: $(SRC_DIR)/ctb_native.c
ctb_native.$O: $(SRC_TOP)Headers/prologue.h
ctb_native.$O: $(BLD_TOP)config.h
ctb_native.$O: $(BLD_TOP)forbuild.h
ctb_native.$O: $(SRC_TOP)Headers/log.h
ctb_native.$O: $(SRC_TOP)Headers/ctb.h
ctb_native.$O: $(SRC_TOP)Headers/ctb_types.h
ctb_native.$O: $(SRC_TOP)Headers/prefs.h
ctb_native.$O: $(SRC_DIR)/ctb_internal.h
ctb_native.$O: $(SRC_DIR)/ctb_translate.h
ctb_native.$O: $(SRC_TOP)Headers/brl_dots.h
ctb_native.$O: $(SRC_TOP)Headers/unicode.h
ctb_native.$O: $(SRC_TOP)Headers/charset.h
ctb_native.$O: $(SRC_TOP)Headers/lock.h
ctb_native.$O: $(SRC_TOP)Headers/ascii.h

# Dependencies for ctb_translate.$O:
ctb_translate.$O: $(SRC_DIR)/ctb_translate.c
ctb_translate.$O: $(SRC_TOP)Headers/prologue.h
ctb_translate.$O: $(BLD_TOP)config.h
ctb_translate.$O: $(BLD_TOP)forbuild.h
ctb_translate.$O: $(SRC_TOP)Headers/log.h
ctb_translate.$O: $(SRC_TOP)Headers/ctb.h
ctb_translate.$O: $(SRC_TOP)Headers/ctb_types.h
ctb_translate.$O: $(SRC_TOP)Headers/prefs.h
ctb_translate.$O: $(SRC_DIR)/ctb_internal.h
ctb_translate.$O: $(SRC_DIR)/ctb_translate.h
ctb_translate.$O: $(SRC_TOP)Headers/ttb.h
ctb_translate.$O: $(SRC_TOP)Headers/unicode.h

# Dependencies for dataarea.$O:
dataarea.$O: $(SRC_DIR)/dataarea.c
dataarea.$O: $(SRC_TOP)Headers/prologue.h
dataarea.$O: $(BLD_TOP)config.h
dataarea.$O: $(BLD_TOP)forbuild.h
dataarea.$O: $(SRC_TOP)Headers/log.h
dataarea.$O: $(SRC_TOP)Headers/dataarea.h

# Dependencies for datafile.$O:
datafile.$O: $(SRC_DIR)/datafile.c
datafile.$O: $(SRC_TOP)Headers/prologue.h
datafile.$O: $(BLD_TOP)config.h
datafile.$O: $(BLD_TOP)forbuild.h
datafile.$O: $(SRC_TOP)Headers/log.h
datafile.$O: $(SRC_TOP)Headers/strfmt.h
datafile.$O: $(SRC_TOP)Headers/strfmth.h
datafile.$O: $(SRC_TOP)Headers/file.h
datafile.$O: $(SRC_TOP)Headers/get_sockets.h
datafile.$O: $(SRC_TOP)Headers/queue.h
datafile.$O: $(SRC_TOP)Headers/datafile.h
datafile.$O: $(SRC_TOP)Headers/variables.h
datafile.$O: $(SRC_TOP)Headers/charset.h
datafile.$O: $(SRC_TOP)Headers/lock.h
datafile.$O: $(SRC_TOP)Headers/unicode.h
datafile.$O: $(SRC_TOP)Headers/brl_dots.h

# Dependencies for device.$O:
device.$O: $(SRC_DIR)/device.c
device.$O: $(SRC_TOP)Headers/prologue.h
device.$O: $(BLD_TOP)config.h
device.$O: $(BLD_TOP)forbuild.h
device.$O: $(SRC_TOP)Headers/log.h
device.$O: $(SRC_TOP)Headers/strfmt.h
device.$O: $(SRC_TOP)Headers/strfmth.h
device.$O: $(SRC_TOP)Headers/device.h
device.$O: $(SRC_TOP)Headers/file.h
device.$O: $(SRC_TOP)Headers/get_sockets.h
device.$O: $(SRC_TOP)Headers/parse.h

# Dependencies for driver.$O:
driver.$O: $(SRC_DIR)/driver.c
driver.$O: $(SRC_TOP)Headers/prologue.h
driver.$O: $(BLD_TOP)config.h
driver.$O: $(BLD_TOP)forbuild.h
driver.$O: $(SRC_TOP)Headers/log.h
driver.$O: $(SRC_TOP)Headers/driver.h

# Dependencies for drivers.$O:
drivers.$O: $(SRC_DIR)/drivers.c
drivers.$O: $(SRC_TOP)Headers/prologue.h
drivers.$O: $(BLD_TOP)config.h
drivers.$O: $(BLD_TOP)forbuild.h
drivers.$O: $(SRC_TOP)Headers/log.h
drivers.$O: $(SRC_TOP)Headers/strfmt.h
drivers.$O: $(SRC_TOP)Headers/strfmth.h
drivers.$O: $(SRC_TOP)Headers/file.h
drivers.$O: $(SRC_TOP)Headers/get_sockets.h
drivers.$O: $(SRC_TOP)Headers/dynld.h
drivers.$O: $(SRC_TOP)Headers/driver.h
drivers.$O: $(SRC_TOP)Headers/drivers.h

# Dependencies for dynld_dlfcn.$O:
dynld_dlfcn.$O: $(SRC_DIR)/dynld_dlfcn.c
dynld_dlfcn.$O: $(SRC_TOP)Headers/prologue.h
dynld_dlfcn.$O: $(BLD_TOP)config.h
dynld_dlfcn.$O: $(BLD_TOP)forbuild.h
dynld_dlfcn.$O: $(SRC_TOP)Headers/pid.h
dynld_dlfcn.$O: $(SRC_TOP)Headers/program.h
dynld_dlfcn.$O: $(SRC_TOP)Headers/log.h
dynld_dlfcn.$O: $(SRC_TOP)Headers/dynld.h

# Dependencies for dynld_dyld.$O:
dynld_dyld.$O: $(SRC_DIR)/dynld_dyld.c
dynld_dyld.$O: $(SRC_TOP)Headers/prologue.h
dynld_dyld.$O: $(BLD_TOP)config.h
dynld_dyld.$O: $(BLD_TOP)forbuild.h
dynld_dyld.$O: $(SRC_TOP)Headers/log.h
dynld_dyld.$O: $(SRC_TOP)Headers/dynld.h

# Dependencies for dynld_grub.$O:
dynld_grub.$O: $(SRC_DIR)/dynld_grub.c
dynld_grub.$O: $(SRC_TOP)Headers/prologue.h
dynld_grub.$O: $(BLD_TOP)config.h
dynld_grub.$O: $(BLD_TOP)forbuild.h
dynld_grub.$O: $(SRC_TOP)Headers/dynld.h

# Dependencies for dynld_none.$O:
dynld_none.$O: $(SRC_DIR)/dynld_none.c
dynld_none.$O: $(SRC_TOP)Headers/prologue.h
dynld_none.$O: $(BLD_TOP)config.h
dynld_none.$O: $(BLD_TOP)forbuild.h
dynld_none.$O: $(SRC_TOP)Headers/dynld.h

# Dependencies for dynld_shl.$O:
dynld_shl.$O: $(SRC_DIR)/dynld_shl.c
dynld_shl.$O: $(SRC_TOP)Headers/prologue.h
dynld_shl.$O: $(BLD_TOP)config.h
dynld_shl.$O: $(BLD_TOP)forbuild.h
dynld_shl.$O: $(SRC_TOP)Headers/log.h
dynld_shl.$O: $(SRC_TOP)Headers/dynld.h

# Dependencies for dynld_windows.$O:
dynld_windows.$O: $(SRC_DIR)/dynld_windows.c
dynld_windows.$O: $(SRC_TOP)Headers/prologue.h
dynld_windows.$O: $(BLD_TOP)config.h
dynld_windows.$O: $(BLD_TOP)forbuild.h
dynld_windows.$O: $(SRC_TOP)Headers/log.h
dynld_windows.$O: $(SRC_TOP)Headers/dynld.h

# Dependencies for file.$O:
file.$O: $(SRC_DIR)/file.c
file.$O: $(SRC_TOP)Headers/prologue.h
file.$O: $(BLD_TOP)config.h
file.$O: $(BLD_TOP)forbuild.h
file.$O: $(SRC_DIR)/parameters.h
file.$O: $(SRC_TOP)Headers/log.h
file.$O: $(SRC_TOP)Headers/strfmt.h
file.$O: $(SRC_TOP)Headers/strfmth.h
file.$O: $(SRC_TOP)Headers/file.h
file.$O: $(SRC_TOP)Headers/get_sockets.h
file.$O: $(SRC_TOP)Headers/parse.h
file.$O: $(SRC_TOP)Headers/async.h
file.$O: $(SRC_TOP)Headers/async_wait.h
file.$O: $(SRC_TOP)Headers/charset.h
file.$O: $(SRC_TOP)Headers/lock.h
file.$O: $(SRC_TOP)Headers/pid.h
file.$O: $(SRC_TOP)Headers/program.h

# Dependencies for fm_adlib.$O:
fm_adlib.$O: $(SRC_DIR)/fm_adlib.c
fm_adlib.$O: $(SRC_TOP)Headers/prologue.h
fm_adlib.$O: $(BLD_TOP)config.h
fm_adlib.$O: $(BLD_TOP)forbuild.h
fm_adlib.$O: $(SRC_TOP)Headers/log.h
fm_adlib.$O: $(SRC_TOP)Headers/async.h
fm_adlib.$O: $(SRC_TOP)Headers/async_wait.h
fm_adlib.$O: $(SRC_TOP)Headers/timing.h
fm_adlib.$O: $(SRC_TOP)Headers/ports.h
fm_adlib.$O: $(SRC_TOP)Headers/fm.h
fm_adlib.$O: $(SRC_TOP)Headers/fm_adlib.h

# Dependencies for fm_none.$O:
fm_none.$O: $(SRC_DIR)/fm_none.c
fm_none.$O: $(SRC_TOP)Headers/prologue.h
fm_none.$O: $(BLD_TOP)config.h
fm_none.$O: $(BLD_TOP)forbuild.h
fm_none.$O: $(SRC_TOP)Headers/fm.h

# Dependencies for gio.$O:
gio.$O: $(SRC_DIR)/gio.c
gio.$O: $(SRC_TOP)Headers/prologue.h
gio.$O: $(BLD_TOP)config.h
gio.$O: $(BLD_TOP)forbuild.h
gio.$O: $(SRC_TOP)Headers/log.h
gio.$O: $(SRC_TOP)Headers/async.h
gio.$O: $(SRC_TOP)Headers/async_wait.h
gio.$O: $(SRC_TOP)Headers/async_alarm.h
gio.$O: $(SRC_TOP)Headers/timing.h
gio.$O: $(SRC_TOP)Headers/async_io.h
gio.$O: $(SRC_TOP)Headers/gio_types.h
gio.$O: $(SRC_TOP)Headers/io_generic.h
gio.$O: $(SRC_TOP)Headers/serial_types.h
gio.$O: $(SRC_TOP)Headers/usb_types.h
gio.$O: $(SRC_DIR)/gio_internal.h
gio.$O: $(SRC_TOP)Headers/io_serial.h

# Dependencies for gio_bluetooth.$O:
gio_bluetooth.$O: $(SRC_DIR)/gio_bluetooth.c
gio_bluetooth.$O: $(SRC_TOP)Headers/prologue.h
gio_bluetooth.$O: $(BLD_TOP)config.h
gio_bluetooth.$O: $(BLD_TOP)forbuild.h
gio_bluetooth.$O: $(SRC_TOP)Headers/log.h
gio_bluetooth.$O: $(SRC_TOP)Headers/async.h
gio_bluetooth.$O: $(SRC_TOP)Headers/async_io.h
gio_bluetooth.$O: $(SRC_TOP)Headers/gio_types.h
gio_bluetooth.$O: $(SRC_TOP)Headers/io_generic.h
gio_bluetooth.$O: $(SRC_TOP)Headers/serial_types.h
gio_bluetooth.$O: $(SRC_TOP)Headers/usb_types.h
gio_bluetooth.$O: $(SRC_DIR)/gio_internal.h
gio_bluetooth.$O: $(SRC_TOP)Headers/io_bluetooth.h
gio_bluetooth.$O: $(SRC_TOP)Headers/api_types.h
gio_bluetooth.$O: $(SRC_TOP)Headers/brl_types.h
gio_bluetooth.$O: $(SRC_TOP)Headers/driver.h
gio_bluetooth.$O: $(SRC_TOP)Headers/ktb_types.h
gio_bluetooth.$O: $(SRC_TOP)Headers/queue.h
gio_bluetooth.$O: $(SRC_DIR)/brl.h

# Dependencies for gio_null.$O:
gio_null.$O: $(SRC_DIR)/gio_null.c
gio_null.$O: $(SRC_TOP)Headers/prologue.h
gio_null.$O: $(BLD_TOP)config.h
gio_null.$O: $(BLD_TOP)forbuild.h
gio_null.$O: $(SRC_TOP)Headers/log.h
gio_null.$O: $(SRC_TOP)Headers/async.h
gio_null.$O: $(SRC_TOP)Headers/async_io.h
gio_null.$O: $(SRC_TOP)Headers/gio_types.h
gio_null.$O: $(SRC_TOP)Headers/io_generic.h
gio_null.$O: $(SRC_TOP)Headers/serial_types.h
gio_null.$O: $(SRC_TOP)Headers/usb_types.h
gio_null.$O: $(SRC_DIR)/gio_internal.h
gio_null.$O: $(SRC_TOP)Headers/parse.h

# Dependencies for gio_serial.$O:
gio_serial.$O: $(SRC_DIR)/gio_serial.c
gio_serial.$O: $(SRC_TOP)Headers/prologue.h
gio_serial.$O: $(BLD_TOP)config.h
gio_serial.$O: $(BLD_TOP)forbuild.h
gio_serial.$O: $(SRC_TOP)Headers/log.h
gio_serial.$O: $(SRC_TOP)Headers/async.h
gio_serial.$O: $(SRC_TOP)Headers/async_io.h
gio_serial.$O: $(SRC_TOP)Headers/gio_types.h
gio_serial.$O: $(SRC_TOP)Headers/io_generic.h
gio_serial.$O: $(SRC_TOP)Headers/serial_types.h
gio_serial.$O: $(SRC_TOP)Headers/usb_types.h
gio_serial.$O: $(SRC_DIR)/gio_internal.h
gio_serial.$O: $(SRC_TOP)Headers/io_serial.h

# Dependencies for gio_usb.$O:
gio_usb.$O: $(SRC_DIR)/gio_usb.c
gio_usb.$O: $(SRC_TOP)Headers/prologue.h
gio_usb.$O: $(BLD_TOP)config.h
gio_usb.$O: $(BLD_TOP)forbuild.h
gio_usb.$O: $(SRC_TOP)Headers/timing.h
gio_usb.$O: $(SRC_TOP)Headers/win_pthread.h
gio_usb.$O: $(SRC_TOP)Headers/log.h
gio_usb.$O: $(SRC_DIR)/parameters.h
gio_usb.$O: $(SRC_TOP)Headers/async.h
gio_usb.$O: $(SRC_TOP)Headers/async_wait.h
gio_usb.$O: $(SRC_TOP)Headers/async_io.h
gio_usb.$O: $(SRC_TOP)Headers/gio_types.h
gio_usb.$O: $(SRC_TOP)Headers/io_generic.h
gio_usb.$O: $(SRC_TOP)Headers/serial_types.h
gio_usb.$O: $(SRC_TOP)Headers/usb_types.h
gio_usb.$O: $(SRC_DIR)/gio_internal.h
gio_usb.$O: $(SRC_TOP)Headers/io_usb.h

# Dependencies for hidkeys.$O:
hidkeys.$O: $(SRC_DIR)/hidkeys.c
hidkeys.$O: $(SRC_TOP)Headers/prologue.h
hidkeys.$O: $(BLD_TOP)config.h
hidkeys.$O: $(BLD_TOP)forbuild.h
hidkeys.$O: $(SRC_DIR)/hidkeys.h
hidkeys.$O: $(SRC_TOP)Headers/kbd_keycodes.h
hidkeys.$O: $(SRC_TOP)Headers/bitmask.h
hidkeys.$O: $(SRC_TOP)Headers/brl_cmds.h
hidkeys.$O: $(SRC_TOP)Headers/brl_dots.h
hidkeys.$O: $(SRC_TOP)Headers/cmd_enqueue.h

# Dependencies for hostcmd.$O:
hostcmd.$O: $(SRC_DIR)/hostcmd.c
hostcmd.$O: $(SRC_TOP)Headers/prologue.h
hostcmd.$O: $(BLD_TOP)config.h
hostcmd.$O: $(BLD_TOP)forbuild.h
hostcmd.$O: $(SRC_TOP)Headers/log.h
hostcmd.$O: $(SRC_TOP)Headers/strfmt.h
hostcmd.$O: $(SRC_TOP)Headers/strfmth.h
hostcmd.$O: $(SRC_TOP)Headers/hostcmd.h
hostcmd.$O: $(SRC_DIR)/hostcmd_none.h
hostcmd.$O: $(SRC_DIR)/hostcmd_unix.h
hostcmd.$O: $(SRC_DIR)/hostcmd_windows.h
hostcmd.$O: $(SRC_DIR)/hostcmd_internal.h

# Dependencies for hostcmd_none.$O:
hostcmd_none.$O: $(SRC_DIR)/hostcmd_none.c
hostcmd_none.$O: $(SRC_TOP)Headers/prologue.h
hostcmd_none.$O: $(BLD_TOP)config.h
hostcmd_none.$O: $(BLD_TOP)forbuild.h
hostcmd_none.$O: $(SRC_DIR)/hostcmd_none.h
hostcmd_none.$O: $(SRC_DIR)/hostcmd_internal.h

# Dependencies for hostcmd_unix.$O:
hostcmd_unix.$O: $(SRC_DIR)/hostcmd_unix.c
hostcmd_unix.$O: $(SRC_TOP)Headers/prologue.h
hostcmd_unix.$O: $(BLD_TOP)config.h
hostcmd_unix.$O: $(BLD_TOP)forbuild.h
hostcmd_unix.$O: $(SRC_TOP)Headers/log.h
hostcmd_unix.$O: $(SRC_DIR)/hostcmd_unix.h
hostcmd_unix.$O: $(SRC_DIR)/hostcmd_internal.h

# Dependencies for hostcmd_windows.$O:
hostcmd_windows.$O: $(SRC_DIR)/hostcmd_windows.c
hostcmd_windows.$O: $(SRC_TOP)Headers/prologue.h
hostcmd_windows.$O: $(BLD_TOP)config.h
hostcmd_windows.$O: $(BLD_TOP)forbuild.h
hostcmd_windows.$O: $(SRC_TOP)Headers/log.h
hostcmd_windows.$O: $(SRC_TOP)Headers/system_windows.h
hostcmd_windows.$O: $(SRC_DIR)/hostcmd_windows.h
hostcmd_windows.$O: $(SRC_DIR)/hostcmd_internal.h

# Dependencies for io_misc.$O:
io_misc.$O: $(SRC_DIR)/io_misc.c
io_misc.$O: $(SRC_TOP)Headers/prologue.h
io_misc.$O: $(BLD_TOP)config.h
io_misc.$O: $(BLD_TOP)forbuild.h
io_misc.$O: $(SRC_TOP)Headers/get_sockets.h
io_misc.$O: $(SRC_TOP)Headers/io_misc.h
io_misc.$O: $(SRC_TOP)Headers/log.h
io_misc.$O: $(SRC_TOP)Headers/file.h
io_misc.$O: $(SRC_TOP)Headers/async.h
io_misc.$O: $(SRC_TOP)Headers/async_wait.h
io_misc.$O: $(SRC_TOP)Headers/async_io.h

# Dependencies for kbd.$O:
kbd.$O: $(SRC_DIR)/kbd.c
kbd.$O: $(SRC_TOP)Headers/prologue.h
kbd.$O: $(BLD_TOP)config.h
kbd.$O: $(BLD_TOP)forbuild.h
kbd.$O: $(SRC_TOP)Headers/log.h
kbd.$O: $(SRC_TOP)Headers/parse.h
kbd.$O: $(SRC_TOP)Headers/bitmask.h
kbd.$O: $(SRC_TOP)Headers/ktb_types.h
kbd.$O: $(SRC_DIR)/kbd.h
kbd.$O: $(SRC_TOP)Headers/queue.h
kbd.$O: $(SRC_DIR)/kbd_internal.h
kbd.$O: $(SRC_DIR)/ktb_keyboard.h

# Dependencies for kbd_android.$O:
kbd_android.$O: $(SRC_DIR)/kbd_android.c
kbd_android.$O: $(SRC_TOP)Headers/prologue.h
kbd_android.$O: $(BLD_TOP)config.h
kbd_android.$O: $(BLD_TOP)forbuild.h
kbd_android.$O: $(SRC_TOP)Headers/log.h
kbd_android.$O: $(SRC_TOP)Headers/common_java.h
kbd_android.$O: $(SRC_TOP)Headers/system_java.h
kbd_android.$O: $(SRC_TOP)Headers/ktb_types.h
kbd_android.$O: $(SRC_DIR)/kbd.h
kbd_android.$O: $(SRC_TOP)Headers/queue.h
kbd_android.$O: $(SRC_DIR)/kbd_internal.h
kbd_android.$O: $(SRC_DIR)/ktb_keyboard.h
kbd_android.$O: $(SRC_DIR)/kbd_android.h

# Dependencies for kbd_keycodes.$O:
kbd_keycodes.$O: $(SRC_DIR)/kbd_keycodes.c
kbd_keycodes.$O: $(SRC_TOP)Headers/prologue.h
kbd_keycodes.$O: $(BLD_TOP)config.h
kbd_keycodes.$O: $(BLD_TOP)forbuild.h
kbd_keycodes.$O: $(SRC_TOP)Headers/kbd_keycodes.h

# Dependencies for kbd_linux.$O:
kbd_linux.$O: $(SRC_DIR)/kbd_linux.c
kbd_linux.$O: $(SRC_TOP)Headers/prologue.h
kbd_linux.$O: $(BLD_TOP)config.h
kbd_linux.$O: $(BLD_TOP)forbuild.h
kbd_linux.$O: $(SRC_DIR)/parameters.h
kbd_linux.$O: $(SRC_TOP)Headers/log.h
kbd_linux.$O: $(SRC_TOP)Headers/strfmt.h
kbd_linux.$O: $(SRC_TOP)Headers/strfmth.h
kbd_linux.$O: $(SRC_TOP)Headers/file.h
kbd_linux.$O: $(SRC_TOP)Headers/get_sockets.h
kbd_linux.$O: $(SRC_TOP)Headers/system_linux.h
kbd_linux.$O: $(SRC_TOP)Headers/ktb_types.h
kbd_linux.$O: $(SRC_DIR)/kbd.h
kbd_linux.$O: $(SRC_TOP)Headers/queue.h
kbd_linux.$O: $(SRC_DIR)/kbd_internal.h
kbd_linux.$O: $(SRC_DIR)/ktb_keyboard.h
kbd_linux.$O: $(SRC_TOP)Headers/bitmask.h
kbd_linux.$O: $(SRC_TOP)Headers/async.h
kbd_linux.$O: $(SRC_TOP)Headers/async_alarm.h
kbd_linux.$O: $(SRC_TOP)Headers/timing.h
kbd_linux.$O: $(SRC_TOP)Headers/async_io.h

# Dependencies for kbd_none.$O:
kbd_none.$O: $(SRC_DIR)/kbd_none.c
kbd_none.$O: $(SRC_TOP)Headers/prologue.h
kbd_none.$O: $(BLD_TOP)config.h
kbd_none.$O: $(BLD_TOP)forbuild.h
kbd_none.$O: $(SRC_TOP)Headers/ktb_types.h
kbd_none.$O: $(SRC_DIR)/kbd.h
kbd_none.$O: $(SRC_TOP)Headers/queue.h
kbd_none.$O: $(SRC_DIR)/kbd_internal.h
kbd_none.$O: $(SRC_DIR)/ktb_keyboard.h

# Dependencies for ktb_audit.$O:
ktb_audit.$O: $(SRC_DIR)/ktb_audit.c
ktb_audit.$O: $(SRC_TOP)Headers/prologue.h
ktb_audit.$O: $(BLD_TOP)config.h
ktb_audit.$O: $(BLD_TOP)forbuild.h
ktb_audit.$O: $(SRC_TOP)Headers/log.h
ktb_audit.$O: $(SRC_TOP)Headers/strfmt.h
ktb_audit.$O: $(SRC_TOP)Headers/strfmth.h
ktb_audit.$O: $(SRC_TOP)Headers/ktb.h
ktb_audit.$O: $(SRC_TOP)Headers/ktb_types.h
ktb_audit.$O: $(SRC_TOP)Headers/async.h
ktb_audit.$O: $(SRC_TOP)Headers/cmd_types.h
ktb_audit.$O: $(SRC_DIR)/ktb_internal.h
ktb_audit.$O: $(SRC_DIR)/ktb_inspect.h

# Dependencies for ktb_cmds.$O:
ktb_cmds.$O: $(SRC_DIR)/ktb_cmds.c
ktb_cmds.$O: $(SRC_TOP)Headers/prologue.h
ktb_cmds.$O: $(BLD_TOP)config.h
ktb_cmds.$O: $(BLD_TOP)forbuild.h
ktb_cmds.$O: $(SRC_DIR)/ktb_cmds.h
ktb_cmds.$O: $(SRC_TOP)Headers/brl_cmds.h
ktb_cmds.$O: $(SRC_TOP)Headers/brl_dots.h

# Dependencies for ktb_compile.$O:
ktb_compile.$O: $(SRC_DIR)/ktb_compile.c
ktb_compile.$O: $(SRC_TOP)Headers/prologue.h
ktb_compile.$O: $(BLD_TOP)config.h
ktb_compile.$O: $(BLD_TOP)forbuild.h
ktb_compile.$O: $(SRC_TOP)Headers/log.h
ktb_compile.$O: $(SRC_TOP)Headers/file.h
ktb_compile.$O: $(SRC_TOP)Headers/get_sockets.h
ktb_compile.$O: $(SRC_TOP)Headers/datafile.h
ktb_compile.$O: $(SRC_TOP)Headers/variables.h
ktb_compile.$O: $(SRC_TOP)Headers/cmd.h
ktb_compile.$O: $(SRC_TOP)Headers/cmd_types.h
ktb_compile.$O: $(SRC_TOP)Headers/strfmth.h
ktb_compile.$O: $(SRC_TOP)Headers/brl_cmds.h
ktb_compile.$O: $(SRC_TOP)Headers/brl_dots.h
ktb_compile.$O: $(SRC_TOP)Headers/ktb.h
ktb_compile.$O: $(SRC_TOP)Headers/ktb_types.h
ktb_compile.$O: $(SRC_TOP)Headers/async.h
ktb_compile.$O: $(SRC_DIR)/ktb_internal.h
ktb_compile.$O: $(SRC_TOP)Headers/pid.h
ktb_compile.$O: $(SRC_TOP)Headers/program.h

# Dependencies for ktb_keyboard.$O:
ktb_keyboard.$O: $(SRC_DIR)/ktb_keyboard.c
ktb_keyboard.$O: $(SRC_TOP)Headers/prologue.h
ktb_keyboard.$O: $(BLD_TOP)config.h
ktb_keyboard.$O: $(BLD_TOP)forbuild.h
ktb_keyboard.$O: $(SRC_TOP)Headers/ktb_types.h
ktb_keyboard.$O: $(SRC_DIR)/ktb_keyboard.h

# Dependencies for ktb_list.$O:
ktb_list.$O: $(SRC_DIR)/ktb_list.c
ktb_list.$O: $(SRC_TOP)Headers/prologue.h
ktb_list.$O: $(BLD_TOP)config.h
ktb_list.$O: $(BLD_TOP)forbuild.h
ktb_list.$O: $(SRC_TOP)Headers/log.h
ktb_list.$O: $(SRC_TOP)Headers/strfmt.h
ktb_list.$O: $(SRC_TOP)Headers/strfmth.h
ktb_list.$O: $(SRC_TOP)Headers/charset.h
ktb_list.$O: $(SRC_TOP)Headers/lock.h
ktb_list.$O: $(SRC_TOP)Headers/cmd.h
ktb_list.$O: $(SRC_TOP)Headers/cmd_types.h
ktb_list.$O: $(SRC_TOP)Headers/brl_cmds.h
ktb_list.$O: $(SRC_TOP)Headers/brl_dots.h
ktb_list.$O: $(SRC_TOP)Headers/ktb.h
ktb_list.$O: $(SRC_TOP)Headers/ktb_types.h
ktb_list.$O: $(SRC_TOP)Headers/async.h
ktb_list.$O: $(SRC_DIR)/ktb_inspect.h
ktb_list.$O: $(SRC_DIR)/ktb_internal.h
ktb_list.$O: $(SRC_DIR)/ktb_list.h
ktb_list.$O: $(SRC_DIR)/ktb_cmds.h

# Dependencies for ktb_translate.$O:
ktb_translate.$O: $(SRC_DIR)/ktb_translate.c
ktb_translate.$O: $(SRC_TOP)Headers/prologue.h
ktb_translate.$O: $(BLD_TOP)config.h
ktb_translate.$O: $(BLD_TOP)forbuild.h
ktb_translate.$O: $(SRC_TOP)Headers/log.h
ktb_translate.$O: $(SRC_TOP)Headers/strfmt.h
ktb_translate.$O: $(SRC_TOP)Headers/strfmth.h
ktb_translate.$O: $(SRC_TOP)Headers/alert.h
ktb_translate.$O: $(SRC_TOP)Headers/prefs.h
ktb_translate.$O: $(SRC_TOP)Headers/ktb.h
ktb_translate.$O: $(SRC_TOP)Headers/ktb_types.h
ktb_translate.$O: $(SRC_TOP)Headers/async.h
ktb_translate.$O: $(SRC_TOP)Headers/cmd_types.h
ktb_translate.$O: $(SRC_DIR)/ktb_internal.h
ktb_translate.$O: $(SRC_DIR)/ktb_inspect.h
ktb_translate.$O: $(SRC_TOP)Headers/brl_cmds.h
ktb_translate.$O: $(SRC_TOP)Headers/brl_dots.h
ktb_translate.$O: $(SRC_TOP)Headers/cmd.h
ktb_translate.$O: $(SRC_TOP)Headers/cmd_enqueue.h
ktb_translate.$O: $(SRC_TOP)Headers/async_alarm.h
ktb_translate.$O: $(SRC_TOP)Headers/timing.h

# Dependencies for learn.$O:
learn.$O: $(SRC_DIR)/learn.c
learn.$O: $(SRC_TOP)Headers/prologue.h
learn.$O: $(BLD_TOP)config.h
learn.$O: $(BLD_TOP)forbuild.h
learn.$O: $(SRC_TOP)Headers/log.h
learn.$O: $(SRC_DIR)/learn.h
learn.$O: $(SRC_TOP)Headers/message.h
learn.$O: $(SRC_TOP)Headers/async.h
learn.$O: $(SRC_TOP)Headers/async_wait.h
learn.$O: $(SRC_TOP)Headers/cmd.h
learn.$O: $(SRC_TOP)Headers/cmd_types.h
learn.$O: $(SRC_TOP)Headers/strfmth.h
learn.$O: $(SRC_TOP)Headers/ktb_types.h
learn.$O: $(SRC_DIR)/cmd_queue.h
learn.$O: $(SRC_TOP)Headers/api_types.h
learn.$O: $(SRC_TOP)Headers/brl_types.h
learn.$O: $(SRC_TOP)Headers/driver.h
learn.$O: $(SRC_TOP)Headers/gio_types.h
learn.$O: $(SRC_TOP)Headers/queue.h
learn.$O: $(SRC_TOP)Headers/serial_types.h
learn.$O: $(SRC_TOP)Headers/usb_types.h
learn.$O: $(SRC_DIR)/brl.h
learn.$O: $(SRC_TOP)Headers/brl_cmds.h
learn.$O: $(SRC_TOP)Headers/brl_dots.h
learn.$O: $(SRC_TOP)Headers/ctb.h
learn.$O: $(SRC_TOP)Headers/ctb_types.h
learn.$O: $(SRC_TOP)Headers/ktb.h
learn.$O: $(SRC_TOP)Headers/pid.h
learn.$O: $(SRC_TOP)Headers/prefs.h
learn.$O: $(SRC_TOP)Headers/program.h
learn.$O: $(SRC_TOP)Headers/scr_types.h
learn.$O: $(SRC_TOP)Headers/spk.h
learn.$O: $(SRC_TOP)Headers/spk_types.h
learn.$O: $(SRC_TOP)Headers/timing.h
learn.$O: $(SRC_DIR)/core.h
learn.$O: $(SRC_DIR)/profile_types.h
learn.$O: $(SRC_DIR)/ses.h

# Dependencies for leds.$O:
leds.$O: $(SRC_DIR)/leds.c
leds.$O: $(SRC_TOP)Headers/prologue.h
leds.$O: $(BLD_TOP)config.h
leds.$O: $(BLD_TOP)forbuild.h
leds.$O: $(SRC_TOP)Headers/leds.h

# Dependencies for leds_linux.$O:
leds_linux.$O: $(SRC_DIR)/leds_linux.c
leds_linux.$O: $(SRC_TOP)Headers/prologue.h
leds_linux.$O: $(BLD_TOP)config.h
leds_linux.$O: $(BLD_TOP)forbuild.h
leds_linux.$O: $(SRC_TOP)Headers/log.h
leds_linux.$O: $(SRC_TOP)Headers/leds.h
leds_linux.$O: $(SRC_TOP)Headers/system_linux.h

# Dependencies for leds_none.$O:
leds_none.$O: $(SRC_DIR)/leds_none.c
leds_none.$O: $(SRC_TOP)Headers/prologue.h
leds_none.$O: $(BLD_TOP)config.h
leds_none.$O: $(BLD_TOP)forbuild.h
leds_none.$O: $(SRC_TOP)Headers/leds.h

# Dependencies for lock.$O:
lock.$O: $(SRC_DIR)/lock.c
lock.$O: $(SRC_TOP)Headers/prologue.h
lock.$O: $(BLD_TOP)config.h
lock.$O: $(BLD_TOP)forbuild.h
lock.$O: $(SRC_TOP)Headers/lock.h
lock.$O: $(SRC_TOP)Headers/log.h
lock.$O: $(SRC_TOP)Headers/get_pthreads.h
lock.$O: $(SRC_TOP)Headers/timing.h
lock.$O: $(SRC_TOP)Headers/win_pthread.h

# Dependencies for log.$O:
log.$O: $(SRC_DIR)/log.c
log.$O: $(SRC_TOP)Headers/prologue.h
log.$O: $(BLD_TOP)config.h
log.$O: $(BLD_TOP)forbuild.h
log.$O: $(SRC_TOP)Headers/log.h
log.$O: $(SRC_TOP)Headers/get_pthreads.h
log.$O: $(SRC_TOP)Headers/log_history.h
log.$O: $(SRC_TOP)Headers/thread.h
log.$O: $(SRC_TOP)Headers/timing.h
log.$O: $(SRC_TOP)Headers/win_pthread.h
log.$O: $(SRC_TOP)Headers/strfmt.h
log.$O: $(SRC_TOP)Headers/strfmth.h
log.$O: $(SRC_TOP)Headers/file.h
log.$O: $(SRC_TOP)Headers/get_sockets.h
log.$O: $(SRC_TOP)Headers/addresses.h
log.$O: $(SRC_TOP)Headers/stdiox.h

# Dependencies for log_history.$O:
log_history.$O: $(SRC_DIR)/log_history.c
log_history.$O: $(SRC_TOP)Headers/prologue.h
log_history.$O: $(BLD_TOP)config.h
log_history.$O: $(BLD_TOP)forbuild.h
log_history.$O: $(SRC_TOP)Headers/log.h
log_history.$O: $(SRC_TOP)Headers/get_pthreads.h
log_history.$O: $(SRC_TOP)Headers/log_history.h
log_history.$O: $(SRC_TOP)Headers/thread.h
log_history.$O: $(SRC_TOP)Headers/timing.h
log_history.$O: $(SRC_TOP)Headers/win_pthread.h

# Dependencies for menu.$O:
menu.$O: $(SRC_DIR)/menu.c
menu.$O: $(SRC_TOP)Headers/prologue.h
menu.$O: $(BLD_TOP)config.h
menu.$O: $(BLD_TOP)forbuild.h
menu.$O: $(SRC_TOP)Headers/log.h
menu.$O: $(SRC_TOP)Headers/menu.h
menu.$O: $(SRC_TOP)Headers/prefs.h
menu.$O: $(SRC_TOP)Headers/parse.h
menu.$O: $(SRC_TOP)Headers/file.h
menu.$O: $(SRC_TOP)Headers/get_sockets.h

# Dependencies for menu_prefs.$O:
menu_prefs.$O: $(SRC_DIR)/menu_prefs.c
menu_prefs.$O: $(SRC_TOP)Headers/prologue.h
menu_prefs.$O: $(BLD_TOP)config.h
menu_prefs.$O: $(BLD_TOP)forbuild.h
menu_prefs.$O: $(SRC_TOP)Headers/log.h
menu_prefs.$O: $(SRC_TOP)Headers/get_pthreads.h
menu_prefs.$O: $(SRC_TOP)Headers/log_history.h
menu_prefs.$O: $(SRC_TOP)Headers/thread.h
menu_prefs.$O: $(SRC_TOP)Headers/timing.h
menu_prefs.$O: $(SRC_TOP)Headers/win_pthread.h
menu_prefs.$O: $(SRC_TOP)Headers/embed.h
menu_prefs.$O: $(SRC_TOP)Headers/pid.h
menu_prefs.$O: $(SRC_TOP)Headers/program.h
menu_prefs.$O: $(SRC_TOP)Headers/revision.h
menu_prefs.$O: $(SRC_TOP)Headers/menu.h
menu_prefs.$O: $(SRC_DIR)/menu_prefs.h
menu_prefs.$O: $(SRC_TOP)Headers/prefs.h
menu_prefs.$O: $(SRC_DIR)/profile.h
menu_prefs.$O: $(SRC_DIR)/profile_types.h
menu_prefs.$O: $(SRC_TOP)Headers/status_types.h
menu_prefs.$O: $(SRC_TOP)Headers/ttb.h
menu_prefs.$O: $(SRC_TOP)Headers/atb.h
menu_prefs.$O: $(SRC_TOP)Headers/ctb.h
menu_prefs.$O: $(SRC_TOP)Headers/ctb_types.h
menu_prefs.$O: $(SRC_TOP)Headers/ktb.h
menu_prefs.$O: $(SRC_TOP)Headers/ktb_types.h
menu_prefs.$O: $(SRC_TOP)Headers/note_types.h
menu_prefs.$O: $(SRC_TOP)Headers/tune.h
menu_prefs.$O: $(SRC_TOP)Headers/tune_types.h
menu_prefs.$O: $(SRC_TOP)Headers/bell.h
menu_prefs.$O: $(SRC_TOP)Headers/leds.h
menu_prefs.$O: $(SRC_TOP)Headers/midi.h
menu_prefs.$O: $(SRC_TOP)Headers/api_types.h
menu_prefs.$O: $(SRC_TOP)Headers/async.h
menu_prefs.$O: $(SRC_TOP)Headers/brl_types.h
menu_prefs.$O: $(SRC_TOP)Headers/cmd.h
menu_prefs.$O: $(SRC_TOP)Headers/cmd_types.h
menu_prefs.$O: $(SRC_TOP)Headers/driver.h
menu_prefs.$O: $(SRC_TOP)Headers/gio_types.h
menu_prefs.$O: $(SRC_TOP)Headers/queue.h
menu_prefs.$O: $(SRC_TOP)Headers/scr_types.h
menu_prefs.$O: $(SRC_TOP)Headers/serial_types.h
menu_prefs.$O: $(SRC_TOP)Headers/spk.h
menu_prefs.$O: $(SRC_TOP)Headers/spk_types.h
menu_prefs.$O: $(SRC_TOP)Headers/strfmth.h
menu_prefs.$O: $(SRC_TOP)Headers/usb_types.h
menu_prefs.$O: $(SRC_DIR)/brl.h
menu_prefs.$O: $(SRC_DIR)/core.h
menu_prefs.$O: $(SRC_DIR)/ses.h

# Dependencies for message.$O:
message.$O: $(SRC_DIR)/message.c
message.$O: $(SRC_TOP)Headers/prologue.h
message.$O: $(BLD_TOP)config.h
message.$O: $(BLD_TOP)forbuild.h
message.$O: $(SRC_TOP)Headers/embed.h
message.$O: $(SRC_TOP)Headers/pid.h
message.$O: $(SRC_TOP)Headers/program.h
message.$O: $(SRC_TOP)Headers/log.h
message.$O: $(SRC_TOP)Headers/get_pthreads.h
message.$O: $(SRC_TOP)Headers/log_history.h
message.$O: $(SRC_TOP)Headers/thread.h
message.$O: $(SRC_TOP)Headers/timing.h
message.$O: $(SRC_TOP)Headers/win_pthread.h
message.$O: $(SRC_TOP)Headers/message.h
message.$O: $(SRC_TOP)Headers/api_types.h
message.$O: $(SRC_TOP)Headers/async.h
message.$O: $(SRC_TOP)Headers/brl_types.h
message.$O: $(SRC_TOP)Headers/ctb_types.h
message.$O: $(SRC_TOP)Headers/driver.h
message.$O: $(SRC_TOP)Headers/gio_types.h
message.$O: $(SRC_TOP)Headers/ktb_types.h
message.$O: $(SRC_TOP)Headers/queue.h
message.$O: $(SRC_TOP)Headers/serial_types.h
message.$O: $(SRC_TOP)Headers/spk_types.h
message.$O: $(SRC_TOP)Headers/tune_types.h
message.$O: $(SRC_TOP)Headers/usb_types.h
message.$O: $(SRC_DIR)/defaults.h
message.$O: $(SRC_DIR)/parameters.h
message.$O: $(SRC_TOP)Headers/async_wait.h
message.$O: $(SRC_TOP)Headers/async_event.h
message.$O: $(SRC_TOP)Headers/async_task.h
message.$O: $(SRC_TOP)Headers/charset.h
message.$O: $(SRC_TOP)Headers/lock.h
message.$O: $(SRC_TOP)Headers/brl_utils.h
message.$O: $(SRC_TOP)Headers/brl_cmds.h
message.$O: $(SRC_TOP)Headers/brl_dots.h
message.$O: $(SRC_TOP)Headers/spk.h
message.$O: $(SRC_DIR)/update.h
message.$O: $(SRC_DIR)/cmd_queue.h
message.$O: $(SRC_DIR)/api_control.h
message.$O: $(SRC_TOP)Headers/cmd.h
message.$O: $(SRC_TOP)Headers/cmd_types.h
message.$O: $(SRC_TOP)Headers/ctb.h
message.$O: $(SRC_TOP)Headers/ktb.h
message.$O: $(SRC_TOP)Headers/prefs.h
message.$O: $(SRC_TOP)Headers/scr_types.h
message.$O: $(SRC_TOP)Headers/strfmth.h
message.$O: $(SRC_DIR)/brl.h
message.$O: $(SRC_DIR)/core.h
message.$O: $(SRC_DIR)/profile_types.h
message.$O: $(SRC_DIR)/ses.h

# Dependencies for midi.$O:
midi.$O: $(SRC_DIR)/midi.c
midi.$O: $(SRC_TOP)Headers/prologue.h
midi.$O: $(BLD_TOP)config.h
midi.$O: $(BLD_TOP)forbuild.h
midi.$O: $(SRC_TOP)Headers/midi.h

# Dependencies for midi_alsa.$O:
midi_alsa.$O: $(SRC_DIR)/midi_alsa.c
midi_alsa.$O: $(SRC_TOP)Headers/prologue.h
midi_alsa.$O: $(BLD_TOP)config.h
midi_alsa.$O: $(BLD_TOP)forbuild.h
midi_alsa.$O: $(SRC_TOP)Headers/log.h
midi_alsa.$O: $(SRC_TOP)Headers/parse.h
midi_alsa.$O: $(SRC_TOP)Headers/timing.h
midi_alsa.$O: $(SRC_TOP)Headers/midi.h

# Dependencies for midi_darwin.$O:
midi_darwin.$O: $(SRC_DIR)/midi_darwin.c
midi_darwin.$O: $(SRC_TOP)Headers/prologue.h
midi_darwin.$O: $(BLD_TOP)config.h
midi_darwin.$O: $(BLD_TOP)forbuild.h
midi_darwin.$O: $(SRC_TOP)Headers/log.h
midi_darwin.$O: $(SRC_TOP)Headers/midi.h

# Dependencies for midi_none.$O:
midi_none.$O: $(SRC_DIR)/midi_none.c
midi_none.$O: $(SRC_TOP)Headers/prologue.h
midi_none.$O: $(BLD_TOP)config.h
midi_none.$O: $(BLD_TOP)forbuild.h
midi_none.$O: $(SRC_TOP)Headers/log.h
midi_none.$O: $(SRC_TOP)Headers/midi.h

# Dependencies for midi_oss.$O:
midi_oss.$O: $(SRC_DIR)/midi_oss.c
midi_oss.$O: $(SRC_TOP)Headers/prologue.h
midi_oss.$O: $(BLD_TOP)config.h
midi_oss.$O: $(BLD_TOP)forbuild.h
midi_oss.$O: $(SRC_TOP)Headers/log.h
midi_oss.$O: $(SRC_TOP)Headers/get_sockets.h
midi_oss.$O: $(SRC_TOP)Headers/io_misc.h
midi_oss.$O: $(SRC_TOP)Headers/midi.h

# Dependencies for midi_windows.$O:
midi_windows.$O: $(SRC_DIR)/midi_windows.c
midi_windows.$O: $(SRC_TOP)Headers/prologue.h
midi_windows.$O: $(BLD_TOP)config.h
midi_windows.$O: $(BLD_TOP)forbuild.h
midi_windows.$O: $(SRC_TOP)Headers/log.h
midi_windows.$O: $(SRC_TOP)Headers/parse.h
midi_windows.$O: $(SRC_TOP)Headers/timing.h
midi_windows.$O: $(SRC_TOP)Headers/midi.h

# Dependencies for mntfs_linux.$O:
mntfs_linux.$O: $(SRC_DIR)/mntfs_linux.c
mntfs_linux.$O: $(SRC_TOP)Headers/prologue.h
mntfs_linux.$O: $(BLD_TOP)config.h
mntfs_linux.$O: $(BLD_TOP)forbuild.h
mntfs_linux.$O: $(SRC_TOP)Headers/mntfs.h

# Dependencies for mntfs_none.$O:
mntfs_none.$O: $(SRC_DIR)/mntfs_none.c
mntfs_none.$O: $(SRC_TOP)Headers/prologue.h
mntfs_none.$O: $(BLD_TOP)config.h
mntfs_none.$O: $(BLD_TOP)forbuild.h
mntfs_none.$O: $(SRC_TOP)Headers/mntfs.h

# Dependencies for mntpt.$O:
mntpt.$O: $(SRC_DIR)/mntpt.c
mntpt.$O: $(SRC_TOP)Headers/prologue.h
mntpt.$O: $(BLD_TOP)config.h
mntpt.$O: $(BLD_TOP)forbuild.h
mntpt.$O: $(SRC_TOP)Headers/log.h
mntpt.$O: $(SRC_DIR)/parameters.h
mntpt.$O: $(SRC_TOP)Headers/mntfs.h
mntpt.$O: $(SRC_TOP)Headers/async.h
mntpt.$O: $(SRC_TOP)Headers/async_alarm.h
mntpt.$O: $(SRC_TOP)Headers/timing.h
mntpt.$O: $(SRC_TOP)Headers/mntpt.h
mntpt.$O: $(SRC_DIR)/mntpt_none.h
mntpt.$O: $(SRC_DIR)/mntpt_mntent.h
mntpt.$O: $(SRC_DIR)/mntpt_mnttab.h
mntpt.$O: $(SRC_DIR)/mntpt_internal.h

# Dependencies for mntpt_mntent.$O:
mntpt_mntent.$O: $(SRC_DIR)/mntpt_mntent.c
mntpt_mntent.$O: $(SRC_TOP)Headers/prologue.h
mntpt_mntent.$O: $(BLD_TOP)config.h
mntpt_mntent.$O: $(BLD_TOP)forbuild.h
mntpt_mntent.$O: $(SRC_TOP)Headers/log.h
mntpt_mntent.$O: $(SRC_DIR)/mntpt_mntent.h
mntpt_mntent.$O: $(SRC_DIR)/mntpt_internal.h

# Dependencies for mntpt_mnttab.$O:
mntpt_mnttab.$O: $(SRC_DIR)/mntpt_mnttab.c
mntpt_mnttab.$O: $(SRC_TOP)Headers/prologue.h
mntpt_mnttab.$O: $(BLD_TOP)config.h
mntpt_mnttab.$O: $(BLD_TOP)forbuild.h
mntpt_mnttab.$O: $(SRC_TOP)Headers/log.h
mntpt_mnttab.$O: $(SRC_DIR)/mntpt_mnttab.h
mntpt_mnttab.$O: $(SRC_DIR)/mntpt_internal.h

# Dependencies for mntpt_none.$O:
mntpt_none.$O: $(SRC_DIR)/mntpt_none.c
mntpt_none.$O: $(SRC_TOP)Headers/prologue.h
mntpt_none.$O: $(BLD_TOP)config.h
mntpt_none.$O: $(BLD_TOP)forbuild.h
mntpt_none.$O: $(SRC_TOP)Headers/log.h
mntpt_none.$O: $(SRC_DIR)/mntpt_none.h
mntpt_none.$O: $(SRC_DIR)/mntpt_internal.h

# Dependencies for morse.$O:
morse.$O: $(SRC_DIR)/morse.c
morse.$O: $(SRC_TOP)Headers/prologue.h
morse.$O: $(BLD_TOP)config.h
morse.$O: $(BLD_TOP)forbuild.h
morse.$O: $(SRC_TOP)Headers/log.h
morse.$O: $(SRC_TOP)Headers/morse.h
morse.$O: $(SRC_TOP)Headers/note_types.h
morse.$O: $(SRC_TOP)Headers/tune.h
morse.$O: $(SRC_TOP)Headers/tune_types.h
morse.$O: $(SRC_TOP)Headers/charset.h
morse.$O: $(SRC_TOP)Headers/lock.h

# Dependencies for notes.$O:
notes.$O: $(SRC_DIR)/notes.c
notes.$O: $(SRC_TOP)Headers/prologue.h
notes.$O: $(BLD_TOP)config.h
notes.$O: $(BLD_TOP)forbuild.h
notes.$O: $(SRC_TOP)Headers/note_types.h
notes.$O: $(SRC_TOP)Headers/notes.h

# Dependencies for notes_beep.$O:
notes_beep.$O: $(SRC_DIR)/notes_beep.c
notes_beep.$O: $(SRC_TOP)Headers/prologue.h
notes_beep.$O: $(BLD_TOP)config.h
notes_beep.$O: $(BLD_TOP)forbuild.h
notes_beep.$O: $(SRC_TOP)Headers/log.h
notes_beep.$O: $(SRC_TOP)Headers/async.h
notes_beep.$O: $(SRC_TOP)Headers/async_wait.h
notes_beep.$O: $(SRC_TOP)Headers/beep.h
notes_beep.$O: $(SRC_TOP)Headers/note_types.h
notes_beep.$O: $(SRC_TOP)Headers/notes.h

# Dependencies for notes_fm.$O:
notes_fm.$O: $(SRC_DIR)/notes_fm.c
notes_fm.$O: $(SRC_TOP)Headers/prologue.h
notes_fm.$O: $(BLD_TOP)config.h
notes_fm.$O: $(BLD_TOP)forbuild.h
notes_fm.$O: $(SRC_TOP)Headers/log.h
notes_fm.$O: $(SRC_TOP)Headers/prefs.h
notes_fm.$O: $(SRC_TOP)Headers/async.h
notes_fm.$O: $(SRC_TOP)Headers/async_wait.h
notes_fm.$O: $(SRC_TOP)Headers/note_types.h
notes_fm.$O: $(SRC_TOP)Headers/notes.h
notes_fm.$O: $(SRC_TOP)Headers/fm.h

# Dependencies for notes_midi.$O:
notes_midi.$O: $(SRC_DIR)/notes_midi.c
notes_midi.$O: $(SRC_TOP)Headers/prologue.h
notes_midi.$O: $(BLD_TOP)config.h
notes_midi.$O: $(BLD_TOP)forbuild.h
notes_midi.$O: $(SRC_TOP)Headers/prefs.h
notes_midi.$O: $(SRC_TOP)Headers/log.h
notes_midi.$O: $(SRC_TOP)Headers/midi.h
notes_midi.$O: $(SRC_TOP)Headers/note_types.h
notes_midi.$O: $(SRC_TOP)Headers/notes.h

# Dependencies for notes_pcm.$O:
notes_pcm.$O: $(SRC_DIR)/notes_pcm.c
notes_pcm.$O: $(SRC_TOP)Headers/prologue.h
notes_pcm.$O: $(BLD_TOP)config.h
notes_pcm.$O: $(BLD_TOP)forbuild.h
notes_pcm.$O: $(SRC_TOP)Headers/prefs.h
notes_pcm.$O: $(SRC_TOP)Headers/log.h
notes_pcm.$O: $(SRC_TOP)Headers/pcm.h
notes_pcm.$O: $(SRC_TOP)Headers/note_types.h
notes_pcm.$O: $(SRC_TOP)Headers/notes.h

# Dependencies for options.$O:
options.$O: $(SRC_DIR)/options.c
options.$O: $(SRC_TOP)Headers/prologue.h
options.$O: $(BLD_TOP)config.h
options.$O: $(BLD_TOP)forbuild.h
options.$O: $(SRC_TOP)Headers/pid.h
options.$O: $(SRC_TOP)Headers/program.h
options.$O: $(SRC_TOP)Headers/datafile.h
options.$O: $(SRC_TOP)Headers/options.h
options.$O: $(SRC_TOP)Headers/strfmth.h
options.$O: $(SRC_TOP)Headers/variables.h
options.$O: $(SRC_TOP)Headers/params.h
options.$O: $(SRC_TOP)Headers/log.h
options.$O: $(SRC_TOP)Headers/file.h
options.$O: $(SRC_TOP)Headers/get_sockets.h
options.$O: $(SRC_TOP)Headers/charset.h
options.$O: $(SRC_TOP)Headers/lock.h
options.$O: $(SRC_TOP)Headers/parse.h

# Dependencies for params_linux.$O:
params_linux.$O: $(SRC_DIR)/params_linux.c
params_linux.$O: $(SRC_TOP)Headers/prologue.h
params_linux.$O: $(BLD_TOP)config.h
params_linux.$O: $(BLD_TOP)forbuild.h
params_linux.$O: $(SRC_TOP)Headers/log.h
params_linux.$O: $(SRC_TOP)Headers/params.h

# Dependencies for params_none.$O:
params_none.$O: $(SRC_DIR)/params_none.c
params_none.$O: $(SRC_TOP)Headers/prologue.h
params_none.$O: $(BLD_TOP)config.h
params_none.$O: $(BLD_TOP)forbuild.h
params_none.$O: $(SRC_TOP)Headers/params.h

# Dependencies for parse.$O:
parse.$O: $(SRC_DIR)/parse.c
parse.$O: $(SRC_TOP)Headers/prologue.h
parse.$O: $(BLD_TOP)config.h
parse.$O: $(BLD_TOP)forbuild.h
parse.$O: $(SRC_TOP)Headers/parse.h
parse.$O: $(SRC_TOP)Headers/log.h

# Dependencies for pcm.$O:
pcm.$O: $(SRC_DIR)/pcm.c
pcm.$O: $(SRC_TOP)Headers/prologue.h
pcm.$O: $(BLD_TOP)config.h
pcm.$O: $(BLD_TOP)forbuild.h
pcm.$O: $(SRC_TOP)Headers/log.h
pcm.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_alsa.$O:
pcm_alsa.$O: $(SRC_DIR)/pcm_alsa.c
pcm_alsa.$O: $(SRC_TOP)Headers/prologue.h
pcm_alsa.$O: $(BLD_TOP)config.h
pcm_alsa.$O: $(BLD_TOP)forbuild.h
pcm_alsa.$O: $(SRC_TOP)Headers/log.h
pcm_alsa.$O: $(SRC_TOP)Headers/timing.h
pcm_alsa.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_android.$O:
pcm_android.$O: $(SRC_DIR)/pcm_android.c
pcm_android.$O: $(SRC_TOP)Headers/prologue.h
pcm_android.$O: $(BLD_TOP)config.h
pcm_android.$O: $(BLD_TOP)forbuild.h
pcm_android.$O: $(SRC_TOP)Headers/log.h
pcm_android.$O: $(SRC_TOP)Headers/pcm.h
pcm_android.$O: $(SRC_TOP)Headers/common_java.h
pcm_android.$O: $(SRC_TOP)Headers/system_java.h

# Dependencies for pcm_audio.$O:
pcm_audio.$O: $(SRC_DIR)/pcm_audio.c
pcm_audio.$O: $(SRC_TOP)Headers/prologue.h
pcm_audio.$O: $(BLD_TOP)config.h
pcm_audio.$O: $(BLD_TOP)forbuild.h
pcm_audio.$O: $(SRC_TOP)Headers/log.h
pcm_audio.$O: $(SRC_TOP)Headers/get_sockets.h
pcm_audio.$O: $(SRC_TOP)Headers/io_misc.h
pcm_audio.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_hpux.$O:
pcm_hpux.$O: $(SRC_DIR)/pcm_hpux.c
pcm_hpux.$O: $(SRC_TOP)Headers/prologue.h
pcm_hpux.$O: $(BLD_TOP)config.h
pcm_hpux.$O: $(BLD_TOP)forbuild.h
pcm_hpux.$O: $(SRC_TOP)Headers/log.h
pcm_hpux.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_none.$O:
pcm_none.$O: $(SRC_DIR)/pcm_none.c
pcm_none.$O: $(SRC_TOP)Headers/prologue.h
pcm_none.$O: $(BLD_TOP)config.h
pcm_none.$O: $(BLD_TOP)forbuild.h
pcm_none.$O: $(SRC_TOP)Headers/log.h
pcm_none.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_oss.$O:
pcm_oss.$O: $(SRC_DIR)/pcm_oss.c
pcm_oss.$O: $(SRC_TOP)Headers/prologue.h
pcm_oss.$O: $(BLD_TOP)config.h
pcm_oss.$O: $(BLD_TOP)forbuild.h
pcm_oss.$O: $(SRC_TOP)Headers/log.h
pcm_oss.$O: $(SRC_TOP)Headers/get_sockets.h
pcm_oss.$O: $(SRC_TOP)Headers/io_misc.h
pcm_oss.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_qsa.$O:
pcm_qsa.$O: $(SRC_DIR)/pcm_qsa.c
pcm_qsa.$O: $(SRC_TOP)Headers/prologue.h
pcm_qsa.$O: $(BLD_TOP)config.h
pcm_qsa.$O: $(BLD_TOP)forbuild.h
pcm_qsa.$O: $(SRC_TOP)Headers/log.h
pcm_qsa.$O: $(SRC_TOP)Headers/get_sockets.h
pcm_qsa.$O: $(SRC_TOP)Headers/io_misc.h
pcm_qsa.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pcm_windows.$O:
pcm_windows.$O: $(SRC_DIR)/pcm_windows.c
pcm_windows.$O: $(SRC_TOP)Headers/prologue.h
pcm_windows.$O: $(BLD_TOP)config.h
pcm_windows.$O: $(BLD_TOP)forbuild.h
pcm_windows.$O: $(SRC_TOP)Headers/log.h
pcm_windows.$O: $(SRC_TOP)Headers/parse.h
pcm_windows.$O: $(SRC_TOP)Headers/pcm.h

# Dependencies for pgmpath_linux.$O:
pgmpath_linux.$O: $(SRC_DIR)/pgmpath_linux.c
pgmpath_linux.$O: $(SRC_TOP)Headers/prologue.h
pgmpath_linux.$O: $(BLD_TOP)config.h
pgmpath_linux.$O: $(BLD_TOP)forbuild.h
pgmpath_linux.$O: $(SRC_TOP)Headers/log.h
pgmpath_linux.$O: $(SRC_TOP)Headers/pgmpath.h

# Dependencies for pgmpath_none.$O:
pgmpath_none.$O: $(SRC_DIR)/pgmpath_none.c
pgmpath_none.$O: $(SRC_TOP)Headers/prologue.h
pgmpath_none.$O: $(BLD_TOP)config.h
pgmpath_none.$O: $(BLD_TOP)forbuild.h
pgmpath_none.$O: $(SRC_TOP)Headers/pgmpath.h

# Dependencies for pgmpath_solaris.$O:
pgmpath_solaris.$O: $(SRC_DIR)/pgmpath_solaris.c
pgmpath_solaris.$O: $(SRC_TOP)Headers/prologue.h
pgmpath_solaris.$O: $(BLD_TOP)config.h
pgmpath_solaris.$O: $(BLD_TOP)forbuild.h
pgmpath_solaris.$O: $(SRC_TOP)Headers/log.h
pgmpath_solaris.$O: $(SRC_TOP)Headers/pgmpath.h

# Dependencies for pgmpath_windows.$O:
pgmpath_windows.$O: $(SRC_DIR)/pgmpath_windows.c
pgmpath_windows.$O: $(SRC_TOP)Headers/prologue.h
pgmpath_windows.$O: $(BLD_TOP)config.h
pgmpath_windows.$O: $(BLD_TOP)forbuild.h
pgmpath_windows.$O: $(SRC_TOP)Headers/log.h
pgmpath_windows.$O: $(SRC_TOP)Headers/pgmpath.h
pgmpath_windows.$O: $(SRC_TOP)Headers/system_windows.h

# Dependencies for pid.$O:
pid.$O: $(SRC_DIR)/pid.c
pid.$O: $(SRC_TOP)Headers/prologue.h
pid.$O: $(BLD_TOP)config.h
pid.$O: $(BLD_TOP)forbuild.h
pid.$O: $(SRC_TOP)Headers/pid.h

# Dependencies for pipe.$O:
pipe.$O: $(SRC_DIR)/pipe.c
pipe.$O: $(SRC_TOP)Headers/prologue.h
pipe.$O: $(BLD_TOP)config.h
pipe.$O: $(BLD_TOP)forbuild.h
pipe.$O: $(SRC_TOP)Headers/log.h
pipe.$O: $(SRC_DIR)/pipe.h
pipe.$O: $(SRC_TOP)Headers/file.h
pipe.$O: $(SRC_TOP)Headers/get_sockets.h
pipe.$O: $(SRC_TOP)Headers/async.h
pipe.$O: $(SRC_TOP)Headers/async_io.h

# Dependencies for ports_glibc.$O:
ports_glibc.$O: $(SRC_DIR)/ports_glibc.c
ports_glibc.$O: $(SRC_TOP)Headers/prologue.h
ports_glibc.$O: $(BLD_TOP)config.h
ports_glibc.$O: $(BLD_TOP)forbuild.h
ports_glibc.$O: $(SRC_TOP)Headers/log.h
ports_glibc.$O: $(SRC_TOP)Headers/ports.h

# Dependencies for ports_grub.$O:
ports_grub.$O: $(SRC_DIR)/ports_grub.c
ports_grub.$O: $(SRC_TOP)Headers/prologue.h
ports_grub.$O: $(BLD_TOP)config.h
ports_grub.$O: $(BLD_TOP)forbuild.h
ports_grub.$O: $(SRC_TOP)Headers/ports.h

# Dependencies for ports_kfreebsd.$O:
ports_kfreebsd.$O: $(SRC_DIR)/ports_kfreebsd.c
ports_kfreebsd.$O: $(SRC_TOP)Headers/prologue.h
ports_kfreebsd.$O: $(BLD_TOP)config.h
ports_kfreebsd.$O: $(BLD_TOP)forbuild.h
ports_kfreebsd.$O: $(SRC_TOP)Headers/log.h
ports_kfreebsd.$O: $(SRC_TOP)Headers/ports.h

# Dependencies for ports_msdos.$O:
ports_msdos.$O: $(SRC_DIR)/ports_msdos.c
ports_msdos.$O: $(SRC_TOP)Headers/prologue.h
ports_msdos.$O: $(BLD_TOP)config.h
ports_msdos.$O: $(BLD_TOP)forbuild.h
ports_msdos.$O: $(SRC_TOP)Headers/ports.h
ports_msdos.$O: $(SRC_DIR)/ports_x86.h

# Dependencies for ports_none.$O:
ports_none.$O: $(SRC_DIR)/ports_none.c
ports_none.$O: $(SRC_TOP)Headers/prologue.h
ports_none.$O: $(BLD_TOP)config.h
ports_none.$O: $(BLD_TOP)forbuild.h
ports_none.$O: $(SRC_TOP)Headers/log.h
ports_none.$O: $(SRC_TOP)Headers/ports.h

# Dependencies for ports_windows.$O:
ports_windows.$O: $(SRC_DIR)/ports_windows.c
ports_windows.$O: $(SRC_TOP)Headers/prologue.h
ports_windows.$O: $(BLD_TOP)config.h
ports_windows.$O: $(BLD_TOP)forbuild.h
ports_windows.$O: $(SRC_TOP)Headers/ports.h
ports_windows.$O: $(SRC_TOP)Headers/system_windows.h
ports_windows.$O: $(SRC_DIR)/ports_x86.h

# Dependencies for prefs.$O:
prefs.$O: $(SRC_DIR)/prefs.c
prefs.$O: $(SRC_TOP)Headers/prologue.h
prefs.$O: $(BLD_TOP)config.h
prefs.$O: $(BLD_TOP)forbuild.h
prefs.$O: $(SRC_TOP)Headers/prefs.h
prefs.$O: $(SRC_DIR)/prefs_internal.h
prefs.$O: $(SRC_TOP)Headers/status_types.h
prefs.$O: $(SRC_TOP)Headers/api_types.h
prefs.$O: $(SRC_TOP)Headers/async.h
prefs.$O: $(SRC_TOP)Headers/brl_types.h
prefs.$O: $(SRC_TOP)Headers/ctb_types.h
prefs.$O: $(SRC_TOP)Headers/driver.h
prefs.$O: $(SRC_TOP)Headers/gio_types.h
prefs.$O: $(SRC_TOP)Headers/ktb_types.h
prefs.$O: $(SRC_TOP)Headers/queue.h
prefs.$O: $(SRC_TOP)Headers/serial_types.h
prefs.$O: $(SRC_TOP)Headers/spk_types.h
prefs.$O: $(SRC_TOP)Headers/tune_types.h
prefs.$O: $(SRC_TOP)Headers/usb_types.h
prefs.$O: $(SRC_DIR)/defaults.h
prefs.$O: $(SRC_DIR)/parameters.h
prefs.$O: $(SRC_TOP)Headers/log.h
prefs.$O: $(SRC_TOP)Headers/file.h
prefs.$O: $(SRC_TOP)Headers/get_sockets.h
prefs.$O: $(SRC_TOP)Headers/datafile.h
prefs.$O: $(SRC_TOP)Headers/variables.h
prefs.$O: $(SRC_TOP)Headers/parse.h

# Dependencies for prefs_table.$O:
prefs_table.$O: $(SRC_DIR)/prefs_table.c
prefs_table.$O: $(SRC_TOP)Headers/prologue.h
prefs_table.$O: $(BLD_TOP)config.h
prefs_table.$O: $(BLD_TOP)forbuild.h
prefs_table.$O: $(SRC_TOP)Headers/prefs.h
prefs_table.$O: $(SRC_DIR)/prefs_internal.h
prefs_table.$O: $(SRC_TOP)Headers/status_types.h
prefs_table.$O: $(SRC_TOP)Headers/api_types.h
prefs_table.$O: $(SRC_TOP)Headers/async.h
prefs_table.$O: $(SRC_TOP)Headers/brl_types.h
prefs_table.$O: $(SRC_TOP)Headers/ctb_types.h
prefs_table.$O: $(SRC_TOP)Headers/driver.h
prefs_table.$O: $(SRC_TOP)Headers/gio_types.h
prefs_table.$O: $(SRC_TOP)Headers/ktb_types.h
prefs_table.$O: $(SRC_TOP)Headers/queue.h
prefs_table.$O: $(SRC_TOP)Headers/serial_types.h
prefs_table.$O: $(SRC_TOP)Headers/spk_types.h
prefs_table.$O: $(SRC_TOP)Headers/tune_types.h
prefs_table.$O: $(SRC_TOP)Headers/usb_types.h
prefs_table.$O: $(SRC_DIR)/defaults.h
prefs_table.$O: $(SRC_DIR)/parameters.h

# Dependencies for profile.$O:
profile.$O: $(SRC_DIR)/profile.c
profile.$O: $(SRC_TOP)Headers/prologue.h
profile.$O: $(BLD_TOP)config.h
profile.$O: $(BLD_TOP)forbuild.h
profile.$O: $(SRC_TOP)Headers/log.h
profile.$O: $(SRC_DIR)/profile.h
profile.$O: $(SRC_DIR)/profile_types.h
profile.$O: $(SRC_TOP)Headers/datafile.h
profile.$O: $(SRC_TOP)Headers/variables.h
profile.$O: $(SRC_TOP)Headers/file.h
profile.$O: $(SRC_TOP)Headers/get_sockets.h
profile.$O: $(SRC_TOP)Headers/charset.h
profile.$O: $(SRC_TOP)Headers/lock.h

# Dependencies for program.$O:
program.$O: $(SRC_DIR)/program.c
program.$O: $(SRC_TOP)Headers/prologue.h
program.$O: $(BLD_TOP)config.h
program.$O: $(BLD_TOP)forbuild.h
program.$O: $(SRC_TOP)Headers/pid.h
program.$O: $(SRC_TOP)Headers/program.h
program.$O: $(SRC_TOP)Headers/pgmpath.h
program.$O: $(SRC_TOP)Headers/log.h
program.$O: $(SRC_TOP)Headers/file.h
program.$O: $(SRC_TOP)Headers/get_sockets.h
program.$O: $(SRC_TOP)Headers/parse.h
program.$O: $(SRC_TOP)Headers/system.h

# Dependencies for queue.$O:
queue.$O: $(SRC_DIR)/queue.c
queue.$O: $(SRC_TOP)Headers/prologue.h
queue.$O: $(BLD_TOP)config.h
queue.$O: $(BLD_TOP)forbuild.h
queue.$O: $(SRC_TOP)Headers/log.h
queue.$O: $(SRC_TOP)Headers/queue.h
queue.$O: $(SRC_TOP)Headers/lock.h
queue.$O: $(SRC_TOP)Headers/pid.h
queue.$O: $(SRC_TOP)Headers/program.h

# Dependencies for report.$O:
report.$O: $(SRC_DIR)/report.c
report.$O: $(SRC_TOP)Headers/prologue.h
report.$O: $(BLD_TOP)config.h
report.$O: $(BLD_TOP)forbuild.h
report.$O: $(SRC_TOP)Headers/log.h
report.$O: $(SRC_TOP)Headers/report.h
report.$O: $(SRC_TOP)Headers/queue.h

# Dependencies for revision.$O:
revision.$O: $(SRC_DIR)/revision.c
revision.$O: $(SRC_TOP)Headers/prologue.h
revision.$O: $(BLD_TOP)config.h
revision.$O: $(BLD_TOP)forbuild.h
revision.$O: $(SRC_TOP)Headers/revision.h
revision.$O: revision_identifier.h

# Dependencies for rgx.$O:
rgx.$O: $(SRC_DIR)/rgx.c
rgx.$O: $(SRC_TOP)Headers/prologue.h
rgx.$O: $(BLD_TOP)config.h
rgx.$O: $(BLD_TOP)forbuild.h
rgx.$O: $(SRC_TOP)Headers/log.h
rgx.$O: $(SRC_TOP)Headers/rgx.h
rgx.$O: $(SRC_TOP)Headers/strfmth.h
rgx.$O: $(SRC_DIR)/rgx_internal.h
rgx.$O: $(SRC_TOP)Headers/charset.h
rgx.$O: $(SRC_TOP)Headers/lock.h
rgx.$O: $(SRC_TOP)Headers/queue.h
rgx.$O: $(SRC_TOP)Headers/strfmt.h

# Dependencies for rgx_libpcre2-32.$O:
rgx_libpcre2-32.$O: $(SRC_DIR)/rgx_libpcre2-32.c
rgx_libpcre2-32.$O: $(SRC_TOP)Headers/prologue.h
rgx_libpcre2-32.$O: $(BLD_TOP)config.h
rgx_libpcre2-32.$O: $(BLD_TOP)forbuild.h
rgx_libpcre2-32.$O: $(SRC_TOP)Headers/rgx.h
rgx_libpcre2-32.$O: $(SRC_TOP)Headers/strfmth.h
rgx_libpcre2-32.$O: $(SRC_DIR)/rgx_internal.h
rgx_libpcre2-32.$O: $(SRC_TOP)Headers/strfmt.h

# Dependencies for rgx_libpcre32.$O:
rgx_libpcre32.$O: $(SRC_DIR)/rgx_libpcre32.c
rgx_libpcre32.$O: $(SRC_TOP)Headers/prologue.h
rgx_libpcre32.$O: $(BLD_TOP)config.h
rgx_libpcre32.$O: $(BLD_TOP)forbuild.h
rgx_libpcre32.$O: $(SRC_TOP)Headers/log.h
rgx_libpcre32.$O: $(SRC_TOP)Headers/rgx.h
rgx_libpcre32.$O: $(SRC_TOP)Headers/strfmth.h
rgx_libpcre32.$O: $(SRC_DIR)/rgx_internal.h
rgx_libpcre32.$O: $(SRC_TOP)Headers/strfmt.h

# Dependencies for rgx_none.$O:
rgx_none.$O: $(SRC_DIR)/rgx_none.c
rgx_none.$O: $(SRC_TOP)Headers/prologue.h
rgx_none.$O: $(BLD_TOP)config.h
rgx_none.$O: $(BLD_TOP)forbuild.h
rgx_none.$O: $(SRC_TOP)Headers/rgx.h
rgx_none.$O: $(SRC_TOP)Headers/strfmth.h
rgx_none.$O: $(SRC_DIR)/rgx_internal.h
rgx_none.$O: $(SRC_TOP)Headers/strfmt.h

# Dependencies for routing.$O:
routing.$O: $(SRC_DIR)/routing.c
routing.$O: $(SRC_TOP)Headers/prologue.h
routing.$O: $(BLD_TOP)config.h
routing.$O: $(BLD_TOP)forbuild.h
routing.$O: $(SRC_TOP)Headers/log.h
routing.$O: $(SRC_TOP)Headers/pid.h
routing.$O: $(SRC_TOP)Headers/program.h
routing.$O: $(SRC_TOP)Headers/get_pthreads.h
routing.$O: $(SRC_TOP)Headers/thread.h
routing.$O: $(SRC_TOP)Headers/timing.h
routing.$O: $(SRC_TOP)Headers/win_pthread.h
routing.$O: $(SRC_TOP)Headers/async.h
routing.$O: $(SRC_TOP)Headers/async_wait.h
routing.$O: $(SRC_TOP)Headers/driver.h
routing.$O: $(SRC_TOP)Headers/ktb_types.h
routing.$O: $(SRC_TOP)Headers/scr_types.h
routing.$O: $(SRC_DIR)/scr.h
routing.$O: $(SRC_DIR)/routing.h

# Dependencies for scr.$O:
scr.$O: $(SRC_DIR)/scr.c
scr.$O: $(SRC_TOP)Headers/prologue.h
scr.$O: $(BLD_TOP)config.h
scr.$O: $(BLD_TOP)forbuild.h
scr.$O: $(SRC_TOP)Headers/log.h
scr.$O: $(SRC_TOP)Headers/driver.h
scr.$O: $(SRC_TOP)Headers/ktb_types.h
scr.$O: $(SRC_TOP)Headers/scr_types.h
scr.$O: $(SRC_DIR)/scr.h
scr.$O: $(SRC_TOP)Headers/scr_base.h
scr.$O: $(SRC_TOP)Headers/scr_main.h
scr.$O: $(SRC_TOP)Headers/scr_real.h

# Dependencies for scr_base.$O:
scr_base.$O: $(SRC_DIR)/scr_base.c
scr_base.$O: $(SRC_TOP)Headers/prologue.h
scr_base.$O: $(BLD_TOP)config.h
scr_base.$O: $(BLD_TOP)forbuild.h
scr_base.$O: $(SRC_TOP)Headers/log.h
scr_base.$O: $(SRC_TOP)Headers/driver.h
scr_base.$O: $(SRC_TOP)Headers/ktb_types.h
scr_base.$O: $(SRC_TOP)Headers/scr_types.h
scr_base.$O: $(SRC_DIR)/scr.h
scr_base.$O: $(SRC_TOP)Headers/scr_utils.h
scr_base.$O: $(SRC_TOP)Headers/scr_base.h
scr_base.$O: $(SRC_TOP)Headers/scr_main.h
scr_base.$O: $(SRC_DIR)/scr_internal.h

# Dependencies for scr_driver.$O:
scr_driver.$O: $(SRC_DIR)/scr_driver.c
scr_driver.$O: $(SRC_TOP)Headers/prologue.h
scr_driver.$O: $(BLD_TOP)config.h
scr_driver.$O: $(BLD_TOP)forbuild.h
scr_driver.$O: $(SRC_TOP)Headers/driver.h
scr_driver.$O: $(SRC_TOP)Headers/drivers.h
scr_driver.$O: $(SRC_TOP)Headers/ktb_types.h
scr_driver.$O: $(SRC_TOP)Headers/scr_types.h
scr_driver.$O: $(SRC_DIR)/scr.h
scr_driver.$O: $(SRC_TOP)Headers/scr_base.h
scr_driver.$O: $(SRC_TOP)Headers/scr_main.h
scr_driver.$O: scr.auto.h
scr_driver.$O: $(SRC_TOP)Headers/scr_driver.h
scr_driver.$O: $(SRC_TOP)Headers/scr_gpm.h
scr_driver.$O: $(SRC_TOP)Headers/scr_real.h
scr_driver.$O: $(SRC_TOP)Headers/scr_utils.h

# Dependencies for scr_frozen.$O:
scr_frozen.$O: $(SRC_DIR)/scr_frozen.c
scr_frozen.$O: $(SRC_TOP)Headers/prologue.h
scr_frozen.$O: $(BLD_TOP)config.h
scr_frozen.$O: $(BLD_TOP)forbuild.h
scr_frozen.$O: $(SRC_TOP)Headers/log.h
scr_frozen.$O: $(SRC_DIR)/parameters.h
scr_frozen.$O: $(SRC_TOP)Headers/async.h
scr_frozen.$O: $(SRC_TOP)Headers/async_alarm.h
scr_frozen.$O: $(SRC_TOP)Headers/timing.h
scr_frozen.$O: $(SRC_TOP)Headers/alert.h
scr_frozen.$O: $(SRC_TOP)Headers/driver.h
scr_frozen.$O: $(SRC_TOP)Headers/ktb_types.h
scr_frozen.$O: $(SRC_TOP)Headers/scr_types.h
scr_frozen.$O: $(SRC_DIR)/scr.h
scr_frozen.$O: $(SRC_TOP)Headers/scr_base.h
scr_frozen.$O: $(SRC_DIR)/scr_frozen.h

# Dependencies for scr_gpm.$O:
scr_gpm.$O: $(SRC_DIR)/scr_gpm.c
scr_gpm.$O: $(SRC_TOP)Headers/prologue.h
scr_gpm.$O: $(BLD_TOP)config.h
scr_gpm.$O: $(BLD_TOP)forbuild.h
scr_gpm.$O: $(SRC_TOP)Headers/log.h
scr_gpm.$O: $(SRC_DIR)/parameters.h
scr_gpm.$O: $(SRC_TOP)Headers/device.h
scr_gpm.$O: $(SRC_TOP)Headers/get_select.h
scr_gpm.$O: $(SRC_TOP)Headers/async.h
scr_gpm.$O: $(SRC_TOP)Headers/async_alarm.h
scr_gpm.$O: $(SRC_TOP)Headers/timing.h
scr_gpm.$O: $(SRC_TOP)Headers/driver.h
scr_gpm.$O: $(SRC_TOP)Headers/ktb_types.h
scr_gpm.$O: $(SRC_TOP)Headers/scr_types.h
scr_gpm.$O: $(SRC_DIR)/scr.h
scr_gpm.$O: $(SRC_TOP)Headers/scr_base.h
scr_gpm.$O: $(SRC_TOP)Headers/scr_gpm.h
scr_gpm.$O: $(SRC_TOP)Headers/scr_main.h

# Dependencies for scr_help.$O:
scr_help.$O: $(SRC_DIR)/scr_help.c
scr_help.$O: $(SRC_TOP)Headers/prologue.h
scr_help.$O: $(BLD_TOP)config.h
scr_help.$O: $(BLD_TOP)forbuild.h
scr_help.$O: $(SRC_TOP)Headers/log.h
scr_help.$O: $(SRC_TOP)Headers/strfmt.h
scr_help.$O: $(SRC_TOP)Headers/strfmth.h
scr_help.$O: $(SRC_TOP)Headers/driver.h
scr_help.$O: $(SRC_TOP)Headers/ktb_types.h
scr_help.$O: $(SRC_TOP)Headers/scr_types.h
scr_help.$O: $(SRC_DIR)/scr.h
scr_help.$O: $(SRC_TOP)Headers/scr_base.h
scr_help.$O: $(SRC_DIR)/scr_help.h

# Dependencies for scr_main.$O:
scr_main.$O: $(SRC_DIR)/scr_main.c
scr_main.$O: $(SRC_TOP)Headers/prologue.h
scr_main.$O: $(BLD_TOP)config.h
scr_main.$O: $(BLD_TOP)forbuild.h
scr_main.$O: $(SRC_DIR)/parameters.h
scr_main.$O: $(SRC_TOP)Headers/api_types.h
scr_main.$O: $(SRC_TOP)Headers/async.h
scr_main.$O: $(SRC_TOP)Headers/brl_types.h
scr_main.$O: $(SRC_TOP)Headers/driver.h
scr_main.$O: $(SRC_TOP)Headers/gio_types.h
scr_main.$O: $(SRC_TOP)Headers/ktb_types.h
scr_main.$O: $(SRC_TOP)Headers/queue.h
scr_main.$O: $(SRC_TOP)Headers/serial_types.h
scr_main.$O: $(SRC_TOP)Headers/usb_types.h
scr_main.$O: $(SRC_DIR)/update.h
scr_main.$O: $(SRC_TOP)Headers/scr_types.h
scr_main.$O: $(SRC_DIR)/scr.h
scr_main.$O: $(SRC_TOP)Headers/scr_base.h
scr_main.$O: $(SRC_TOP)Headers/scr_main.h

# Dependencies for scr_menu.$O:
scr_menu.$O: $(SRC_DIR)/scr_menu.c
scr_menu.$O: $(SRC_TOP)Headers/prologue.h
scr_menu.$O: $(BLD_TOP)config.h
scr_menu.$O: $(BLD_TOP)forbuild.h
scr_menu.$O: $(SRC_TOP)Headers/log.h
scr_menu.$O: $(SRC_TOP)Headers/strfmt.h
scr_menu.$O: $(SRC_TOP)Headers/strfmth.h
scr_menu.$O: $(SRC_TOP)Headers/driver.h
scr_menu.$O: $(SRC_TOP)Headers/ktb_types.h
scr_menu.$O: $(SRC_TOP)Headers/scr_types.h
scr_menu.$O: $(SRC_DIR)/scr.h
scr_menu.$O: $(SRC_TOP)Headers/menu.h
scr_menu.$O: $(SRC_TOP)Headers/scr_base.h
scr_menu.$O: $(SRC_DIR)/scr_menu.h
scr_menu.$O: $(SRC_TOP)Headers/brl_cmds.h
scr_menu.$O: $(SRC_TOP)Headers/brl_dots.h
scr_menu.$O: $(SRC_DIR)/cmd_queue.h
scr_menu.$O: $(SRC_TOP)Headers/alert.h
scr_menu.$O: $(SRC_TOP)Headers/charset.h
scr_menu.$O: $(SRC_TOP)Headers/lock.h
scr_menu.$O: $(SRC_TOP)Headers/api_types.h
scr_menu.$O: $(SRC_TOP)Headers/async.h
scr_menu.$O: $(SRC_TOP)Headers/brl_types.h
scr_menu.$O: $(SRC_TOP)Headers/cmd.h
scr_menu.$O: $(SRC_TOP)Headers/cmd_types.h
scr_menu.$O: $(SRC_TOP)Headers/ctb.h
scr_menu.$O: $(SRC_TOP)Headers/ctb_types.h
scr_menu.$O: $(SRC_TOP)Headers/gio_types.h
scr_menu.$O: $(SRC_TOP)Headers/ktb.h
scr_menu.$O: $(SRC_TOP)Headers/pid.h
scr_menu.$O: $(SRC_TOP)Headers/prefs.h
scr_menu.$O: $(SRC_TOP)Headers/program.h
scr_menu.$O: $(SRC_TOP)Headers/queue.h
scr_menu.$O: $(SRC_TOP)Headers/serial_types.h
scr_menu.$O: $(SRC_TOP)Headers/spk.h
scr_menu.$O: $(SRC_TOP)Headers/spk_types.h
scr_menu.$O: $(SRC_TOP)Headers/timing.h
scr_menu.$O: $(SRC_TOP)Headers/usb_types.h
scr_menu.$O: $(SRC_DIR)/brl.h
scr_menu.$O: $(SRC_DIR)/core.h
scr_menu.$O: $(SRC_DIR)/profile_types.h
scr_menu.$O: $(SRC_DIR)/ses.h

# Dependencies for scr_real.$O:
scr_real.$O: $(SRC_DIR)/scr_real.c
scr_real.$O: $(SRC_TOP)Headers/prologue.h
scr_real.$O: $(BLD_TOP)config.h
scr_real.$O: $(BLD_TOP)forbuild.h
scr_real.$O: $(SRC_TOP)Headers/log.h
scr_real.$O: $(SRC_TOP)Headers/driver.h
scr_real.$O: $(SRC_TOP)Headers/ktb_types.h
scr_real.$O: $(SRC_TOP)Headers/scr_types.h
scr_real.$O: $(SRC_DIR)/scr.h
scr_real.$O: $(SRC_TOP)Headers/scr_base.h
scr_real.$O: $(SRC_TOP)Headers/scr_main.h
scr_real.$O: $(SRC_TOP)Headers/scr_real.h
scr_real.$O: $(SRC_DIR)/routing.h

# Dependencies for scr_special.$O:
scr_special.$O: $(SRC_DIR)/scr_special.c
scr_special.$O: $(SRC_TOP)Headers/prologue.h
scr_special.$O: $(BLD_TOP)config.h
scr_special.$O: $(BLD_TOP)forbuild.h
scr_special.$O: $(SRC_TOP)Headers/log.h
scr_special.$O: $(SRC_TOP)Headers/driver.h
scr_special.$O: $(SRC_TOP)Headers/ktb_types.h
scr_special.$O: $(SRC_TOP)Headers/scr_types.h
scr_special.$O: $(SRC_DIR)/scr.h
scr_special.$O: $(SRC_TOP)Headers/scr_base.h
scr_special.$O: $(SRC_TOP)Headers/scr_main.h
scr_special.$O: $(SRC_DIR)/scr_internal.h
scr_special.$O: $(SRC_DIR)/scr_special.h
scr_special.$O: $(SRC_TOP)Headers/api_types.h
scr_special.$O: $(SRC_TOP)Headers/async.h
scr_special.$O: $(SRC_TOP)Headers/brl_types.h
scr_special.$O: $(SRC_TOP)Headers/gio_types.h
scr_special.$O: $(SRC_TOP)Headers/queue.h
scr_special.$O: $(SRC_TOP)Headers/serial_types.h
scr_special.$O: $(SRC_TOP)Headers/usb_types.h
scr_special.$O: $(SRC_DIR)/update.h
scr_special.$O: $(SRC_TOP)Headers/message.h
scr_special.$O: $(SRC_DIR)/scr_frozen.h
scr_special.$O: $(SRC_DIR)/scr_help.h
scr_special.$O: $(SRC_TOP)Headers/menu.h
scr_special.$O: $(SRC_DIR)/scr_menu.h
scr_special.$O: $(SRC_DIR)/menu_prefs.h

# Dependencies for scr_utils.$O:
scr_utils.$O: $(SRC_DIR)/scr_utils.c
scr_utils.$O: $(SRC_TOP)Headers/prologue.h
scr_utils.$O: $(BLD_TOP)config.h
scr_utils.$O: $(BLD_TOP)forbuild.h
scr_utils.$O: $(SRC_TOP)Headers/scr_types.h
scr_utils.$O: $(SRC_TOP)Headers/scr_utils.h

# Dependencies for scrtest.$O:
scrtest.$O: $(SRC_DIR)/scrtest.c
scrtest.$O: $(SRC_TOP)Headers/prologue.h
scrtest.$O: $(BLD_TOP)config.h
scrtest.$O: $(BLD_TOP)forbuild.h
scrtest.$O: $(SRC_TOP)Headers/pid.h
scrtest.$O: $(SRC_TOP)Headers/program.h
scrtest.$O: $(SRC_TOP)Headers/datafile.h
scrtest.$O: $(SRC_TOP)Headers/options.h
scrtest.$O: $(SRC_TOP)Headers/strfmth.h
scrtest.$O: $(SRC_TOP)Headers/variables.h
scrtest.$O: $(SRC_TOP)Headers/log.h
scrtest.$O: $(SRC_TOP)Headers/parse.h
scrtest.$O: $(SRC_TOP)Headers/driver.h
scrtest.$O: $(SRC_TOP)Headers/ktb_types.h
scrtest.$O: $(SRC_TOP)Headers/scr_types.h
scrtest.$O: $(SRC_DIR)/scr.h
scrtest.$O: $(SRC_TOP)Headers/api_types.h
scrtest.$O: $(SRC_TOP)Headers/async.h
scrtest.$O: $(SRC_TOP)Headers/brl_types.h
scrtest.$O: $(SRC_TOP)Headers/gio_types.h
scrtest.$O: $(SRC_TOP)Headers/queue.h
scrtest.$O: $(SRC_TOP)Headers/serial_types.h
scrtest.$O: $(SRC_TOP)Headers/usb_types.h
scrtest.$O: $(SRC_DIR)/update.h

# Dependencies for serial.$O:
serial.$O: $(SRC_DIR)/serial.c
serial.$O: $(SRC_TOP)Headers/prologue.h
serial.$O: $(BLD_TOP)config.h
serial.$O: $(BLD_TOP)forbuild.h
serial.$O: $(SRC_TOP)Headers/log.h
serial.$O: $(SRC_DIR)/parameters.h
serial.$O: $(SRC_TOP)Headers/parse.h
serial.$O: $(SRC_TOP)Headers/device.h
serial.$O: $(SRC_TOP)Headers/async.h
serial.$O: $(SRC_TOP)Headers/async_wait.h
serial.$O: $(SRC_DIR)/serial_none.h
serial.$O: $(SRC_DIR)/serial_grub.h
serial.$O: $(SRC_DIR)/serial_uart.h
serial.$O: $(SRC_DIR)/serial_msdos.h
serial.$O: $(SRC_DIR)/serial_termios.h
serial.$O: $(SRC_DIR)/serial_windows.h
serial.$O: $(SRC_TOP)Headers/async_io.h
serial.$O: $(SRC_TOP)Headers/get_pthreads.h
serial.$O: $(SRC_TOP)Headers/io_serial.h
serial.$O: $(SRC_TOP)Headers/serial_types.h
serial.$O: $(SRC_TOP)Headers/thread.h
serial.$O: $(SRC_TOP)Headers/timing.h
serial.$O: $(SRC_TOP)Headers/win_pthread.h
serial.$O: $(SRC_DIR)/serial_internal.h

# Dependencies for serial_grub.$O:
serial_grub.$O: $(SRC_DIR)/serial_grub.c
serial_grub.$O: $(SRC_TOP)Headers/prologue.h
serial_grub.$O: $(BLD_TOP)config.h
serial_grub.$O: $(BLD_TOP)forbuild.h
serial_grub.$O: $(SRC_TOP)Headers/log.h
serial_grub.$O: $(SRC_TOP)Headers/timing.h
serial_grub.$O: $(SRC_DIR)/serial_grub.h
serial_grub.$O: $(SRC_DIR)/serial_uart.h
serial_grub.$O: $(SRC_TOP)Headers/async.h
serial_grub.$O: $(SRC_TOP)Headers/async_io.h
serial_grub.$O: $(SRC_TOP)Headers/get_pthreads.h
serial_grub.$O: $(SRC_TOP)Headers/io_serial.h
serial_grub.$O: $(SRC_TOP)Headers/serial_types.h
serial_grub.$O: $(SRC_TOP)Headers/thread.h
serial_grub.$O: $(SRC_TOP)Headers/win_pthread.h
serial_grub.$O: $(SRC_DIR)/serial_internal.h

# Dependencies for serial_msdos.$O:
serial_msdos.$O: $(SRC_DIR)/serial_msdos.c
serial_msdos.$O: $(SRC_TOP)Headers/prologue.h
serial_msdos.$O: $(BLD_TOP)config.h
serial_msdos.$O: $(BLD_TOP)forbuild.h
serial_msdos.$O: $(SRC_TOP)Headers/log.h
serial_msdos.$O: $(SRC_TOP)Headers/async.h
serial_msdos.$O: $(SRC_TOP)Headers/async_wait.h
serial_msdos.$O: $(SRC_TOP)Headers/timing.h
serial_msdos.$O: $(SRC_TOP)Headers/get_sockets.h
serial_msdos.$O: $(SRC_TOP)Headers/io_misc.h
serial_msdos.$O: $(SRC_TOP)Headers/ports.h
serial_msdos.$O: $(SRC_DIR)/serial_msdos.h
serial_msdos.$O: $(SRC_DIR)/serial_uart.h
serial_msdos.$O: $(SRC_TOP)Headers/async_io.h
serial_msdos.$O: $(SRC_TOP)Headers/get_pthreads.h
serial_msdos.$O: $(SRC_TOP)Headers/io_serial.h
serial_msdos.$O: $(SRC_TOP)Headers/serial_types.h
serial_msdos.$O: $(SRC_TOP)Headers/thread.h
serial_msdos.$O: $(SRC_TOP)Headers/win_pthread.h
serial_msdos.$O: $(SRC_DIR)/serial_internal.h

# Dependencies for serial_none.$O:
serial_none.$O: $(SRC_DIR)/serial_none.c
serial_none.$O: $(SRC_TOP)Headers/prologue.h
serial_none.$O: $(BLD_TOP)config.h
serial_none.$O: $(BLD_TOP)forbuild.h
serial_none.$O: $(SRC_DIR)/serial_none.h
serial_none.$O: $(SRC_TOP)Headers/async.h
serial_none.$O: $(SRC_TOP)Headers/async_io.h
serial_none.$O: $(SRC_TOP)Headers/get_pthreads.h
serial_none.$O: $(SRC_TOP)Headers/io_serial.h
serial_none.$O: $(SRC_TOP)Headers/serial_types.h
serial_none.$O: $(SRC_TOP)Headers/thread.h
serial_none.$O: $(SRC_TOP)Headers/timing.h
serial_none.$O: $(SRC_TOP)Headers/win_pthread.h
serial_none.$O: $(SRC_DIR)/serial_internal.h

# Dependencies for serial_termios.$O:
serial_termios.$O: $(SRC_DIR)/serial_termios.c
serial_termios.$O: $(SRC_TOP)Headers/prologue.h
serial_termios.$O: $(BLD_TOP)config.h
serial_termios.$O: $(BLD_TOP)forbuild.h
serial_termios.$O: $(SRC_TOP)Headers/log.h
serial_termios.$O: $(SRC_TOP)Headers/get_sockets.h
serial_termios.$O: $(SRC_TOP)Headers/io_misc.h
serial_termios.$O: $(SRC_TOP)Headers/async.h
serial_termios.$O: $(SRC_DIR)/serial_termios.h
serial_termios.$O: $(SRC_TOP)Headers/async_io.h
serial_termios.$O: $(SRC_TOP)Headers/get_pthreads.h
serial_termios.$O: $(SRC_TOP)Headers/io_serial.h
serial_termios.$O: $(SRC_TOP)Headers/serial_types.h
serial_termios.$O: $(SRC_TOP)Headers/thread.h
serial_termios.$O: $(SRC_TOP)Headers/timing.h
serial_termios.$O: $(SRC_TOP)Headers/win_pthread.h
serial_termios.$O: $(SRC_DIR)/serial_internal.h

# Dependencies for serial_windows.$O:
serial_windows.$O: $(SRC_DIR)/serial_windows.c
serial_windows.$O: $(SRC_TOP)Headers/prologue.h
serial_windows.$O: $(BLD_TOP)config.h
serial_windows.$O: $(BLD_TOP)forbuild.h
serial_windows.$O: $(SRC_TOP)Headers/log.h
serial_windows.$O: $(SRC_TOP)Headers/ascii.h
serial_windows.$O: $(SRC_DIR)/serial_windows.h
serial_windows.$O: $(SRC_TOP)Headers/async.h
serial_windows.$O: $(SRC_TOP)Headers/async_io.h
serial_windows.$O: $(SRC_TOP)Headers/get_pthreads.h
serial_windows.$O: $(SRC_TOP)Headers/io_serial.h
serial_windows.$O: $(SRC_TOP)Headers/serial_types.h
serial_windows.$O: $(SRC_TOP)Headers/thread.h
serial_windows.$O: $(SRC_TOP)Headers/timing.h
serial_windows.$O: $(SRC_TOP)Headers/win_pthread.h
serial_windows.$O: $(SRC_DIR)/serial_internal.h

# Dependencies for service_libsystemd.$O:
service_libsystemd.$O: $(SRC_DIR)/service_libsystemd.c
service_libsystemd.$O: $(SRC_TOP)Headers/prologue.h
service_libsystemd.$O: $(BLD_TOP)config.h
service_libsystemd.$O: $(BLD_TOP)forbuild.h
service_libsystemd.$O: $(SRC_TOP)Headers/log.h
service_libsystemd.$O: $(SRC_TOP)Headers/service.h

# Dependencies for service_none.$O:
service_none.$O: $(SRC_DIR)/service_none.c
service_none.$O: $(SRC_TOP)Headers/prologue.h
service_none.$O: $(BLD_TOP)config.h
service_none.$O: $(BLD_TOP)forbuild.h
service_none.$O: $(SRC_TOP)Headers/log.h
service_none.$O: $(SRC_TOP)Headers/service.h

# Dependencies for service_windows.$O:
service_windows.$O: $(SRC_DIR)/service_windows.c
service_windows.$O: $(SRC_TOP)Headers/prologue.h
service_windows.$O: $(BLD_TOP)config.h
service_windows.$O: $(BLD_TOP)forbuild.h
service_windows.$O: $(SRC_TOP)Headers/log.h
service_windows.$O: $(SRC_TOP)Headers/pgmpath.h
service_windows.$O: $(SRC_TOP)Headers/service.h
service_windows.$O: $(SRC_TOP)Headers/system_windows.h

# Dependencies for ses.$O:
ses.$O: $(SRC_DIR)/ses.c
ses.$O: $(SRC_TOP)Headers/prologue.h
ses.$O: $(BLD_TOP)config.h
ses.$O: $(BLD_TOP)forbuild.h
ses.$O: $(SRC_TOP)Headers/log.h
ses.$O: $(SRC_DIR)/ses.h
ses.$O: $(SRC_TOP)Headers/api_types.h
ses.$O: $(SRC_TOP)Headers/async.h
ses.$O: $(SRC_TOP)Headers/brl_types.h
ses.$O: $(SRC_TOP)Headers/ctb_types.h
ses.$O: $(SRC_TOP)Headers/driver.h
ses.$O: $(SRC_TOP)Headers/gio_types.h
ses.$O: $(SRC_TOP)Headers/ktb_types.h
ses.$O: $(SRC_TOP)Headers/queue.h
ses.$O: $(SRC_TOP)Headers/serial_types.h
ses.$O: $(SRC_TOP)Headers/spk_types.h
ses.$O: $(SRC_TOP)Headers/tune_types.h
ses.$O: $(SRC_TOP)Headers/usb_types.h
ses.$O: $(SRC_DIR)/defaults.h
ses.$O: $(SRC_DIR)/parameters.h

# Dependencies for spk.$O:
spk.$O: $(SRC_DIR)/spk.c
spk.$O: $(SRC_TOP)Headers/prologue.h
spk.$O: $(BLD_TOP)config.h
spk.$O: $(BLD_TOP)forbuild.h
spk.$O: $(SRC_TOP)Headers/log.h
spk.$O: $(SRC_DIR)/parameters.h
spk.$O: $(SRC_TOP)Headers/pid.h
spk.$O: $(SRC_TOP)Headers/program.h
spk.$O: $(SRC_TOP)Headers/file.h
spk.$O: $(SRC_TOP)Headers/get_sockets.h
spk.$O: $(SRC_TOP)Headers/parse.h
spk.$O: $(SRC_TOP)Headers/prefs.h
spk.$O: $(SRC_TOP)Headers/charset.h
spk.$O: $(SRC_TOP)Headers/lock.h
spk.$O: $(SRC_TOP)Headers/driver.h
spk.$O: $(SRC_TOP)Headers/spk.h
spk.$O: $(SRC_TOP)Headers/spk_types.h
spk.$O: $(SRC_DIR)/spk_thread.h

# Dependencies for spk_base.$O:
spk_base.$O: $(SRC_DIR)/spk_base.c
spk_base.$O: $(SRC_TOP)Headers/prologue.h
spk_base.$O: $(BLD_TOP)config.h
spk_base.$O: $(BLD_TOP)forbuild.h
spk_base.$O: $(SRC_TOP)Headers/log.h
spk_base.$O: $(SRC_TOP)Headers/driver.h
spk_base.$O: $(SRC_TOP)Headers/spk_types.h
spk_base.$O: $(SRC_TOP)Headers/spk_base.h
spk_base.$O: $(SRC_DIR)/spk_thread.h
spk_base.$O: $(SRC_TOP)Headers/parse.h

# Dependencies for spk_driver.$O:
spk_driver.$O: $(SRC_DIR)/spk_driver.c
spk_driver.$O: $(SRC_TOP)Headers/prologue.h
spk_driver.$O: $(BLD_TOP)config.h
spk_driver.$O: $(BLD_TOP)forbuild.h
spk_driver.$O: $(SRC_TOP)Headers/driver.h
spk_driver.$O: $(SRC_TOP)Headers/drivers.h
spk_driver.$O: $(SRC_TOP)Headers/spk.h
spk_driver.$O: $(SRC_TOP)Headers/spk_types.h
spk_driver.$O: spk.auto.h
spk_driver.$O: $(SRC_TOP)Headers/spk_base.h
spk_driver.$O: $(SRC_TOP)Headers/spk_driver.h

# Dependencies for spk_input.$O:
spk_input.$O: $(SRC_DIR)/spk_input.c
spk_input.$O: $(SRC_TOP)Headers/prologue.h
spk_input.$O: $(BLD_TOP)config.h
spk_input.$O: $(BLD_TOP)forbuild.h
spk_input.$O: $(SRC_TOP)Headers/log.h
spk_input.$O: $(SRC_DIR)/spk_input.h
spk_input.$O: $(SRC_TOP)Headers/driver.h
spk_input.$O: $(SRC_TOP)Headers/spk.h
spk_input.$O: $(SRC_TOP)Headers/spk_types.h
spk_input.$O: $(SRC_DIR)/pipe.h
spk_input.$O: $(SRC_TOP)Headers/api_types.h
spk_input.$O: $(SRC_TOP)Headers/async.h
spk_input.$O: $(SRC_TOP)Headers/brl_types.h
spk_input.$O: $(SRC_TOP)Headers/cmd.h
spk_input.$O: $(SRC_TOP)Headers/cmd_types.h
spk_input.$O: $(SRC_TOP)Headers/ctb.h
spk_input.$O: $(SRC_TOP)Headers/ctb_types.h
spk_input.$O: $(SRC_TOP)Headers/gio_types.h
spk_input.$O: $(SRC_TOP)Headers/ktb.h
spk_input.$O: $(SRC_TOP)Headers/ktb_types.h
spk_input.$O: $(SRC_TOP)Headers/pid.h
spk_input.$O: $(SRC_TOP)Headers/prefs.h
spk_input.$O: $(SRC_TOP)Headers/program.h
spk_input.$O: $(SRC_TOP)Headers/queue.h
spk_input.$O: $(SRC_TOP)Headers/scr_types.h
spk_input.$O: $(SRC_TOP)Headers/serial_types.h
spk_input.$O: $(SRC_TOP)Headers/strfmth.h
spk_input.$O: $(SRC_TOP)Headers/timing.h
spk_input.$O: $(SRC_TOP)Headers/usb_types.h
spk_input.$O: $(SRC_DIR)/brl.h
spk_input.$O: $(SRC_DIR)/core.h
spk_input.$O: $(SRC_DIR)/profile_types.h
spk_input.$O: $(SRC_DIR)/ses.h

# Dependencies for spk_thread.$O:
spk_thread.$O: $(SRC_DIR)/spk_thread.c
spk_thread.$O: $(SRC_TOP)Headers/prologue.h
spk_thread.$O: $(BLD_TOP)config.h
spk_thread.$O: $(BLD_TOP)forbuild.h
spk_thread.$O: $(SRC_DIR)/parameters.h
spk_thread.$O: $(SRC_TOP)Headers/log.h
spk_thread.$O: $(SRC_TOP)Headers/strfmt.h
spk_thread.$O: $(SRC_TOP)Headers/strfmth.h
spk_thread.$O: $(SRC_TOP)Headers/prefs.h
spk_thread.$O: $(SRC_TOP)Headers/driver.h
spk_thread.$O: $(SRC_TOP)Headers/spk_types.h
spk_thread.$O: $(SRC_DIR)/spk_thread.h
spk_thread.$O: $(SRC_TOP)Headers/spk.h
spk_thread.$O: $(SRC_TOP)Headers/async.h
spk_thread.$O: $(SRC_TOP)Headers/async_wait.h
spk_thread.$O: $(SRC_TOP)Headers/async_event.h
spk_thread.$O: $(SRC_TOP)Headers/get_pthreads.h
spk_thread.$O: $(SRC_TOP)Headers/thread.h
spk_thread.$O: $(SRC_TOP)Headers/timing.h
spk_thread.$O: $(SRC_TOP)Headers/win_pthread.h
spk_thread.$O: $(SRC_TOP)Headers/queue.h

# Dependencies for spktest.$O:
spktest.$O: $(SRC_DIR)/spktest.c
spktest.$O: $(SRC_TOP)Headers/prologue.h
spktest.$O: $(BLD_TOP)config.h
spktest.$O: $(BLD_TOP)forbuild.h
spktest.$O: $(SRC_TOP)Headers/pid.h
spktest.$O: $(SRC_TOP)Headers/program.h
spktest.$O: $(SRC_TOP)Headers/datafile.h
spktest.$O: $(SRC_TOP)Headers/options.h
spktest.$O: $(SRC_TOP)Headers/strfmth.h
spktest.$O: $(SRC_TOP)Headers/variables.h
spktest.$O: $(SRC_TOP)Headers/log.h
spktest.$O: $(SRC_TOP)Headers/driver.h
spktest.$O: $(SRC_TOP)Headers/spk.h
spktest.$O: $(SRC_TOP)Headers/spk_types.h
spktest.$O: $(SRC_TOP)Headers/file.h
spktest.$O: $(SRC_TOP)Headers/get_sockets.h
spktest.$O: $(SRC_TOP)Headers/parse.h
spktest.$O: $(SRC_TOP)Headers/async.h
spktest.$O: $(SRC_TOP)Headers/async_wait.h

# Dependencies for status.$O:
status.$O: $(SRC_DIR)/status.c
status.$O: $(SRC_TOP)Headers/prologue.h
status.$O: $(BLD_TOP)config.h
status.$O: $(BLD_TOP)forbuild.h
status.$O: $(SRC_TOP)Headers/status_types.h
status.$O: $(SRC_DIR)/status.h
status.$O: $(SRC_TOP)Headers/timing.h
status.$O: $(SRC_TOP)Headers/api_types.h
status.$O: $(SRC_TOP)Headers/async.h
status.$O: $(SRC_TOP)Headers/brl_types.h
status.$O: $(SRC_TOP)Headers/driver.h
status.$O: $(SRC_TOP)Headers/gio_types.h
status.$O: $(SRC_TOP)Headers/ktb_types.h
status.$O: $(SRC_TOP)Headers/queue.h
status.$O: $(SRC_TOP)Headers/serial_types.h
status.$O: $(SRC_TOP)Headers/usb_types.h
status.$O: $(SRC_DIR)/update.h
status.$O: $(SRC_TOP)Headers/brl_dots.h
status.$O: $(SRC_TOP)Headers/brl_utils.h
status.$O: $(SRC_TOP)Headers/scr_base.h
status.$O: $(SRC_TOP)Headers/scr_main.h
status.$O: $(SRC_TOP)Headers/scr_types.h
status.$O: $(SRC_DIR)/scr_internal.h
status.$O: $(SRC_DIR)/scr_special.h
status.$O: $(SRC_TOP)Headers/cmd.h
status.$O: $(SRC_TOP)Headers/cmd_types.h
status.$O: $(SRC_TOP)Headers/ctb.h
status.$O: $(SRC_TOP)Headers/ctb_types.h
status.$O: $(SRC_TOP)Headers/ktb.h
status.$O: $(SRC_TOP)Headers/pid.h
status.$O: $(SRC_TOP)Headers/prefs.h
status.$O: $(SRC_TOP)Headers/program.h
status.$O: $(SRC_TOP)Headers/spk.h
status.$O: $(SRC_TOP)Headers/spk_types.h
status.$O: $(SRC_TOP)Headers/strfmth.h
status.$O: $(SRC_DIR)/brl.h
status.$O: $(SRC_DIR)/core.h
status.$O: $(SRC_DIR)/profile_types.h
status.$O: $(SRC_DIR)/ses.h
status.$O: $(SRC_TOP)Headers/ttb.h

# Dependencies for sys_darwin.$O:
sys_darwin.$O: $(SRC_DIR)/sys_darwin.c
sys_darwin.$O: $(SRC_TOP)Headers/prologue.h
sys_darwin.$O: $(BLD_TOP)config.h
sys_darwin.$O: $(BLD_TOP)forbuild.h
sys_darwin.$O: $(SRC_TOP)Headers/system.h

# Dependencies for sys_freebsd.$O:
sys_freebsd.$O: $(SRC_DIR)/sys_freebsd.c
sys_freebsd.$O: $(SRC_TOP)Headers/prologue.h
sys_freebsd.$O: $(BLD_TOP)config.h
sys_freebsd.$O: $(BLD_TOP)forbuild.h
sys_freebsd.$O: $(SRC_TOP)Headers/system.h

# Dependencies for sys_kfreebsd.$O:
sys_kfreebsd.$O: $(SRC_DIR)/sys_kfreebsd.c
sys_kfreebsd.$O: $(SRC_TOP)Headers/prologue.h
sys_kfreebsd.$O: $(BLD_TOP)config.h
sys_kfreebsd.$O: $(BLD_TOP)forbuild.h
sys_kfreebsd.$O: $(SRC_TOP)Headers/system.h

# Dependencies for sys_netbsd.$O:
sys_netbsd.$O: $(SRC_DIR)/sys_netbsd.c
sys_netbsd.$O: $(SRC_TOP)Headers/prologue.h
sys_netbsd.$O: $(BLD_TOP)config.h
sys_netbsd.$O: $(BLD_TOP)forbuild.h
sys_netbsd.$O: $(SRC_TOP)Headers/system.h

# Dependencies for sys_openbsd.$O:
sys_openbsd.$O: $(SRC_DIR)/sys_openbsd.c
sys_openbsd.$O: $(SRC_TOP)Headers/prologue.h
sys_openbsd.$O: $(BLD_TOP)config.h
sys_openbsd.$O: $(BLD_TOP)forbuild.h
sys_openbsd.$O: $(SRC_TOP)Headers/system.h

# Dependencies for sys_solaris.$O:
sys_solaris.$O: $(SRC_DIR)/sys_solaris.c
sys_solaris.$O: $(SRC_TOP)Headers/prologue.h
sys_solaris.$O: $(BLD_TOP)config.h
sys_solaris.$O: $(BLD_TOP)forbuild.h
sys_solaris.$O: $(SRC_TOP)Headers/log.h
sys_solaris.$O: $(SRC_TOP)Headers/system.h

# Dependencies for system_darwin.$O:
system_darwin.$O: $(SRC_DIR)/system_darwin.c
system_darwin.$O: $(SRC_TOP)Headers/prologue.h
system_darwin.$O: $(BLD_TOP)config.h
system_darwin.$O: $(BLD_TOP)forbuild.h
system_darwin.$O: $(SRC_TOP)Headers/log.h
system_darwin.$O: $(SRC_TOP)Headers/system.h
system_darwin.$O: $(SRC_TOP)Headers/system_darwin.h

# Dependencies for system_java.$O:
system_java.$O: $(SRC_DIR)/system_java.c
system_java.$O: $(SRC_TOP)Headers/prologue.h
system_java.$O: $(BLD_TOP)config.h
system_java.$O: $(BLD_TOP)forbuild.h
system_java.$O: $(SRC_TOP)Headers/log.h
system_java.$O: $(SRC_TOP)Headers/get_pthreads.h
system_java.$O: $(SRC_TOP)Headers/thread.h
system_java.$O: $(SRC_TOP)Headers/timing.h
system_java.$O: $(SRC_TOP)Headers/win_pthread.h
system_java.$O: $(SRC_TOP)Headers/system.h
system_java.$O: $(SRC_TOP)Headers/common_java.h
system_java.$O: $(SRC_TOP)Headers/system_java.h

# Dependencies for system_linux.$O:
system_linux.$O: $(SRC_DIR)/system_linux.c
system_linux.$O: $(SRC_TOP)Headers/prologue.h
system_linux.$O: $(BLD_TOP)config.h
system_linux.$O: $(BLD_TOP)forbuild.h
system_linux.$O: $(SRC_TOP)Headers/log.h
system_linux.$O: $(SRC_TOP)Headers/file.h
system_linux.$O: $(SRC_TOP)Headers/get_sockets.h
system_linux.$O: $(SRC_TOP)Headers/device.h
system_linux.$O: $(SRC_TOP)Headers/async.h
system_linux.$O: $(SRC_TOP)Headers/async_wait.h
system_linux.$O: $(SRC_TOP)Headers/async_io.h
system_linux.$O: $(SRC_TOP)Headers/hostcmd.h
system_linux.$O: $(SRC_TOP)Headers/bitmask.h
system_linux.$O: $(SRC_TOP)Headers/system.h
system_linux.$O: $(SRC_TOP)Headers/system_linux.h
system_linux.$O: $(SRC_TOP)Headers/kbd_keycodes.h

# Dependencies for system_msdos.$O:
system_msdos.$O: $(SRC_DIR)/system_msdos.c
system_msdos.$O: $(SRC_TOP)Headers/prologue.h
system_msdos.$O: $(BLD_TOP)config.h
system_msdos.$O: $(BLD_TOP)forbuild.h
system_msdos.$O: $(SRC_TOP)Headers/log.h
system_msdos.$O: $(SRC_TOP)Headers/system.h
system_msdos.$O: $(SRC_TOP)Headers/system_msdos.h

# Dependencies for system_none.$O:
system_none.$O: $(SRC_DIR)/system_none.c
system_none.$O: $(SRC_TOP)Headers/prologue.h
system_none.$O: $(BLD_TOP)config.h
system_none.$O: $(BLD_TOP)forbuild.h
system_none.$O: $(SRC_TOP)Headers/system.h

# Dependencies for system_windows.$O:
system_windows.$O: $(SRC_DIR)/system_windows.c
system_windows.$O: $(SRC_TOP)Headers/prologue.h
system_windows.$O: $(BLD_TOP)config.h
system_windows.$O: $(BLD_TOP)forbuild.h
system_windows.$O: $(SRC_TOP)Headers/log.h
system_windows.$O: $(SRC_TOP)Headers/timing.h
system_windows.$O: $(SRC_TOP)Headers/system.h
system_windows.$O: $(SRC_TOP)Headers/system_windows.h
system_windows.$O: $(SRC_TOP)Headers/win_errno.h

# Dependencies for tbl2hex.$O:
tbl2hex.$O: $(SRC_DIR)/tbl2hex.c
tbl2hex.$O: $(SRC_TOP)Headers/prologue.h
tbl2hex.$O: $(BLD_TOP)config.h
tbl2hex.$O: $(BLD_TOP)forbuild.h
tbl2hex.$O: $(SRC_TOP)Headers/datafile.h
tbl2hex.$O: $(SRC_TOP)Headers/options.h
tbl2hex.$O: $(SRC_TOP)Headers/pid.h
tbl2hex.$O: $(SRC_TOP)Headers/program.h
tbl2hex.$O: $(SRC_TOP)Headers/strfmth.h
tbl2hex.$O: $(SRC_TOP)Headers/variables.h
tbl2hex.$O: $(SRC_TOP)Headers/log.h
tbl2hex.$O: $(SRC_TOP)Headers/file.h
tbl2hex.$O: $(SRC_TOP)Headers/get_sockets.h
tbl2hex.$O: $(SRC_TOP)Headers/ttb.h
tbl2hex.$O: $(SRC_TOP)Headers/bitmask.h
tbl2hex.$O: $(SRC_TOP)Headers/dataarea.h
tbl2hex.$O: $(SRC_TOP)Headers/unicode.h
tbl2hex.$O: $(SRC_DIR)/ttb_internal.h
tbl2hex.$O: $(SRC_TOP)Headers/atb.h
tbl2hex.$O: $(SRC_DIR)/atb_internal.h
tbl2hex.$O: $(SRC_TOP)Headers/ctb.h
tbl2hex.$O: $(SRC_TOP)Headers/ctb_types.h
tbl2hex.$O: $(SRC_DIR)/ctb_internal.h

# Dependencies for thread.$O:
thread.$O: $(SRC_DIR)/thread.c
thread.$O: $(SRC_TOP)Headers/prologue.h
thread.$O: $(BLD_TOP)config.h
thread.$O: $(BLD_TOP)forbuild.h
thread.$O: $(SRC_TOP)Headers/log.h
thread.$O: $(SRC_TOP)Headers/strfmt.h
thread.$O: $(SRC_TOP)Headers/strfmth.h
thread.$O: $(SRC_TOP)Headers/get_pthreads.h
thread.$O: $(SRC_TOP)Headers/thread.h
thread.$O: $(SRC_TOP)Headers/timing.h
thread.$O: $(SRC_TOP)Headers/win_pthread.h
thread.$O: $(SRC_TOP)Headers/async.h
thread.$O: $(SRC_TOP)Headers/async_signal.h
thread.$O: $(SRC_TOP)Headers/async_event.h
thread.$O: $(SRC_TOP)Headers/async_wait.h
thread.$O: $(SRC_TOP)Headers/pid.h
thread.$O: $(SRC_TOP)Headers/program.h

# Dependencies for timing.$O:
timing.$O: $(SRC_DIR)/timing.c
timing.$O: $(SRC_TOP)Headers/prologue.h
timing.$O: $(BLD_TOP)config.h
timing.$O: $(BLD_TOP)forbuild.h
timing.$O: $(SRC_TOP)Headers/log.h
timing.$O: $(SRC_TOP)Headers/timing.h
timing.$O: $(SRC_TOP)Headers/system_msdos.h

# Dependencies for ttb_compile.$O:
ttb_compile.$O: $(SRC_DIR)/ttb_compile.c
ttb_compile.$O: $(SRC_TOP)Headers/prologue.h
ttb_compile.$O: $(BLD_TOP)config.h
ttb_compile.$O: $(BLD_TOP)forbuild.h
ttb_compile.$O: $(SRC_TOP)Headers/log.h
ttb_compile.$O: $(SRC_TOP)Headers/file.h
ttb_compile.$O: $(SRC_TOP)Headers/get_sockets.h
ttb_compile.$O: $(SRC_TOP)Headers/datafile.h
ttb_compile.$O: $(SRC_TOP)Headers/variables.h
ttb_compile.$O: $(SRC_TOP)Headers/dataarea.h
ttb_compile.$O: $(SRC_TOP)Headers/charset.h
ttb_compile.$O: $(SRC_TOP)Headers/lock.h
ttb_compile.$O: $(SRC_TOP)Headers/ttb.h
ttb_compile.$O: $(SRC_TOP)Headers/bitmask.h
ttb_compile.$O: $(SRC_TOP)Headers/unicode.h
ttb_compile.$O: $(SRC_DIR)/ttb_internal.h
ttb_compile.$O: $(SRC_DIR)/ttb_compile.h

# Dependencies for ttb_gnome.$O:
ttb_gnome.$O: $(SRC_DIR)/ttb_gnome.c
ttb_gnome.$O: $(SRC_TOP)Headers/prologue.h
ttb_gnome.$O: $(BLD_TOP)config.h
ttb_gnome.$O: $(BLD_TOP)forbuild.h
ttb_gnome.$O: $(SRC_TOP)Headers/ttb.h
ttb_gnome.$O: $(SRC_TOP)Headers/bitmask.h
ttb_gnome.$O: $(SRC_TOP)Headers/dataarea.h
ttb_gnome.$O: $(SRC_TOP)Headers/unicode.h
ttb_gnome.$O: $(SRC_DIR)/ttb_internal.h
ttb_gnome.$O: $(SRC_TOP)Headers/datafile.h
ttb_gnome.$O: $(SRC_TOP)Headers/variables.h
ttb_gnome.$O: $(SRC_DIR)/ttb_compile.h

# Dependencies for ttb_louis.$O:
ttb_louis.$O: $(SRC_DIR)/ttb_louis.c
ttb_louis.$O: $(SRC_TOP)Headers/prologue.h
ttb_louis.$O: $(BLD_TOP)config.h
ttb_louis.$O: $(BLD_TOP)forbuild.h
ttb_louis.$O: $(SRC_TOP)Headers/ttb.h
ttb_louis.$O: $(SRC_TOP)Headers/bitmask.h
ttb_louis.$O: $(SRC_TOP)Headers/dataarea.h
ttb_louis.$O: $(SRC_TOP)Headers/unicode.h
ttb_louis.$O: $(SRC_DIR)/ttb_internal.h
ttb_louis.$O: $(SRC_TOP)Headers/datafile.h
ttb_louis.$O: $(SRC_TOP)Headers/variables.h
ttb_louis.$O: $(SRC_DIR)/ttb_compile.h

# Dependencies for ttb_native.$O:
ttb_native.$O: $(SRC_DIR)/ttb_native.c
ttb_native.$O: $(SRC_TOP)Headers/prologue.h
ttb_native.$O: $(BLD_TOP)config.h
ttb_native.$O: $(BLD_TOP)forbuild.h
ttb_native.$O: $(SRC_TOP)Headers/file.h
ttb_native.$O: $(SRC_TOP)Headers/get_sockets.h
ttb_native.$O: $(SRC_TOP)Headers/ttb.h
ttb_native.$O: $(SRC_TOP)Headers/bitmask.h
ttb_native.$O: $(SRC_TOP)Headers/dataarea.h
ttb_native.$O: $(SRC_TOP)Headers/unicode.h
ttb_native.$O: $(SRC_DIR)/ttb_internal.h
ttb_native.$O: $(SRC_TOP)Headers/datafile.h
ttb_native.$O: $(SRC_TOP)Headers/variables.h
ttb_native.$O: $(SRC_DIR)/ttb_compile.h

# Dependencies for ttb_translate.$O:
ttb_translate.$O: $(SRC_DIR)/ttb_translate.c
ttb_translate.$O: $(SRC_TOP)Headers/prologue.h
ttb_translate.$O: $(BLD_TOP)config.h
ttb_translate.$O: $(BLD_TOP)forbuild.h
ttb_translate.$O: $(SRC_TOP)Headers/log.h
ttb_translate.$O: $(SRC_TOP)Headers/file.h
ttb_translate.$O: $(SRC_TOP)Headers/get_sockets.h
ttb_translate.$O: $(SRC_TOP)Headers/charset.h
ttb_translate.$O: $(SRC_TOP)Headers/lock.h
ttb_translate.$O: $(SRC_TOP)Headers/ttb.h
ttb_translate.$O: $(SRC_TOP)Headers/bitmask.h
ttb_translate.$O: $(SRC_TOP)Headers/dataarea.h
ttb_translate.$O: $(SRC_TOP)Headers/unicode.h
ttb_translate.$O: $(SRC_DIR)/ttb_internal.h
ttb_translate.$O: $(SRC_TOP)Headers/brl_dots.h
ttb_translate.$O: text.auto.h

# Dependencies for tune.$O:
tune.$O: $(SRC_DIR)/tune.c
tune.$O: $(SRC_TOP)Headers/prologue.h
tune.$O: $(BLD_TOP)config.h
tune.$O: $(BLD_TOP)forbuild.h
tune.$O: $(SRC_TOP)Headers/log.h
tune.$O: $(SRC_DIR)/parameters.h
tune.$O: $(SRC_TOP)Headers/get_pthreads.h
tune.$O: $(SRC_TOP)Headers/thread.h
tune.$O: $(SRC_TOP)Headers/timing.h
tune.$O: $(SRC_TOP)Headers/win_pthread.h
tune.$O: $(SRC_TOP)Headers/async.h
tune.$O: $(SRC_TOP)Headers/async_event.h
tune.$O: $(SRC_TOP)Headers/async_alarm.h
tune.$O: $(SRC_TOP)Headers/async_wait.h
tune.$O: $(SRC_TOP)Headers/pid.h
tune.$O: $(SRC_TOP)Headers/program.h
tune.$O: $(SRC_TOP)Headers/note_types.h
tune.$O: $(SRC_TOP)Headers/tune.h
tune.$O: $(SRC_TOP)Headers/tune_types.h
tune.$O: $(SRC_TOP)Headers/notes.h

# Dependencies for tune_build.$O:
tune_build.$O: $(SRC_DIR)/tune_build.c
tune_build.$O: $(SRC_TOP)Headers/prologue.h
tune_build.$O: $(BLD_TOP)config.h
tune_build.$O: $(BLD_TOP)forbuild.h
tune_build.$O: $(SRC_TOP)Headers/log.h
tune_build.$O: $(SRC_TOP)Headers/note_types.h
tune_build.$O: $(SRC_TOP)Headers/tune.h
tune_build.$O: $(SRC_TOP)Headers/tune_build.h
tune_build.$O: $(SRC_TOP)Headers/tune_types.h
tune_build.$O: $(SRC_TOP)Headers/notes.h
tune_build.$O: $(SRC_TOP)Headers/charset.h
tune_build.$O: $(SRC_TOP)Headers/lock.h

# Dependencies for tune_utils.$O:
tune_utils.$O: $(SRC_DIR)/tune_utils.c
tune_utils.$O: $(SRC_TOP)Headers/prologue.h
tune_utils.$O: $(BLD_TOP)config.h
tune_utils.$O: $(BLD_TOP)forbuild.h
tune_utils.$O: $(SRC_TOP)Headers/log.h
tune_utils.$O: $(SRC_TOP)Headers/note_types.h
tune_utils.$O: $(SRC_TOP)Headers/tune.h
tune_utils.$O: $(SRC_TOP)Headers/tune_types.h
tune_utils.$O: $(SRC_TOP)Headers/tune_utils.h
tune_utils.$O: $(SRC_TOP)Headers/midi.h
tune_utils.$O: $(SRC_TOP)Headers/parse.h
tune_utils.$O: $(SRC_TOP)Headers/prefs.h

# Dependencies for unicode.$O:
unicode.$O: $(SRC_DIR)/unicode.c
unicode.$O: $(SRC_TOP)Headers/prologue.h
unicode.$O: $(BLD_TOP)config.h
unicode.$O: $(BLD_TOP)forbuild.h
unicode.$O: $(SRC_TOP)Headers/log.h
unicode.$O: $(SRC_TOP)Headers/unicode.h
unicode.$O: $(SRC_TOP)Headers/ascii.h

# Dependencies for update.$O:
update.$O: $(SRC_DIR)/update.c
update.$O: $(SRC_TOP)Headers/prologue.h
update.$O: $(BLD_TOP)config.h
update.$O: $(BLD_TOP)forbuild.h
update.$O: $(SRC_DIR)/parameters.h
update.$O: $(SRC_TOP)Headers/log.h
update.$O: $(SRC_TOP)Headers/alert.h
update.$O: $(SRC_TOP)Headers/report.h
update.$O: $(SRC_TOP)Headers/strfmt.h
update.$O: $(SRC_TOP)Headers/strfmth.h
update.$O: $(SRC_TOP)Headers/api_types.h
update.$O: $(SRC_TOP)Headers/async.h
update.$O: $(SRC_TOP)Headers/brl_types.h
update.$O: $(SRC_TOP)Headers/driver.h
update.$O: $(SRC_TOP)Headers/gio_types.h
update.$O: $(SRC_TOP)Headers/ktb_types.h
update.$O: $(SRC_TOP)Headers/queue.h
update.$O: $(SRC_TOP)Headers/serial_types.h
update.$O: $(SRC_TOP)Headers/usb_types.h
update.$O: $(SRC_DIR)/update.h
update.$O: $(SRC_TOP)Headers/async_alarm.h
update.$O: $(SRC_TOP)Headers/timing.h
update.$O: $(SRC_TOP)Headers/unicode.h
update.$O: $(SRC_TOP)Headers/charset.h
update.$O: $(SRC_TOP)Headers/lock.h
update.$O: $(SRC_TOP)Headers/ttb.h
update.$O: $(SRC_TOP)Headers/atb.h
update.$O: $(SRC_TOP)Headers/brl_dots.h
update.$O: $(SRC_TOP)Headers/spk.h
update.$O: $(SRC_TOP)Headers/spk_types.h
update.$O: $(SRC_TOP)Headers/scr_types.h
update.$O: $(SRC_DIR)/scr.h
update.$O: $(SRC_TOP)Headers/scr_base.h
update.$O: $(SRC_TOP)Headers/scr_main.h
update.$O: $(SRC_DIR)/scr_internal.h
update.$O: $(SRC_DIR)/scr_special.h
update.$O: $(SRC_TOP)Headers/scr_utils.h
update.$O: $(SRC_TOP)Headers/prefs.h
update.$O: $(SRC_TOP)Headers/status_types.h
update.$O: $(SRC_DIR)/status.h
update.$O: $(SRC_DIR)/blink.h
update.$O: $(SRC_DIR)/routing.h
update.$O: $(SRC_DIR)/api_control.h
update.$O: $(SRC_TOP)Headers/cmd.h
update.$O: $(SRC_TOP)Headers/cmd_types.h
update.$O: $(SRC_TOP)Headers/ctb.h
update.$O: $(SRC_TOP)Headers/ctb_types.h
update.$O: $(SRC_TOP)Headers/ktb.h
update.$O: $(SRC_TOP)Headers/pid.h
update.$O: $(SRC_TOP)Headers/program.h
update.$O: $(SRC_DIR)/brl.h
update.$O: $(SRC_DIR)/core.h
update.$O: $(SRC_DIR)/profile_types.h
update.$O: $(SRC_DIR)/ses.h

# Dependencies for usb.$O:
usb.$O: $(SRC_DIR)/usb.c
usb.$O: $(SRC_TOP)Headers/prologue.h
usb.$O: $(BLD_TOP)config.h
usb.$O: $(BLD_TOP)forbuild.h
usb.$O: $(SRC_TOP)Headers/log.h
usb.$O: $(SRC_TOP)Headers/strfmt.h
usb.$O: $(SRC_TOP)Headers/strfmth.h
usb.$O: $(SRC_DIR)/parameters.h
usb.$O: $(SRC_TOP)Headers/bitmask.h
usb.$O: $(SRC_TOP)Headers/parse.h
usb.$O: $(SRC_TOP)Headers/file.h
usb.$O: $(SRC_TOP)Headers/get_sockets.h
usb.$O: $(SRC_TOP)Headers/charset.h
usb.$O: $(SRC_TOP)Headers/lock.h
usb.$O: $(SRC_TOP)Headers/device.h
usb.$O: $(SRC_TOP)Headers/timing.h
usb.$O: $(SRC_TOP)Headers/async.h
usb.$O: $(SRC_TOP)Headers/async_wait.h
usb.$O: $(SRC_TOP)Headers/async_alarm.h
usb.$O: $(SRC_TOP)Headers/io_misc.h
usb.$O: $(SRC_TOP)Headers/async_io.h
usb.$O: $(SRC_TOP)Headers/io_usb.h
usb.$O: $(SRC_TOP)Headers/serial_types.h
usb.$O: $(SRC_TOP)Headers/usb_types.h
usb.$O: $(SRC_TOP)Headers/bitfield.h
usb.$O: $(SRC_TOP)Headers/queue.h
usb.$O: $(SRC_DIR)/usb_internal.h
usb.$O: $(SRC_DIR)/usb_devices.h
usb.$O: $(SRC_DIR)/usb_serial.h

# Dependencies for usb_adapters.$O:
usb_adapters.$O: $(SRC_DIR)/usb_adapters.c
usb_adapters.$O: $(SRC_TOP)Headers/prologue.h
usb_adapters.$O: $(BLD_TOP)config.h
usb_adapters.$O: $(BLD_TOP)forbuild.h
usb_adapters.$O: $(SRC_TOP)Headers/async.h
usb_adapters.$O: $(SRC_TOP)Headers/async_io.h
usb_adapters.$O: $(SRC_TOP)Headers/io_usb.h
usb_adapters.$O: $(SRC_TOP)Headers/serial_types.h
usb_adapters.$O: $(SRC_TOP)Headers/usb_types.h
usb_adapters.$O: $(SRC_DIR)/usb_adapters.h
usb_adapters.$O: $(SRC_DIR)/usb_serial.h

# Dependencies for usb_android.$O:
usb_android.$O: $(SRC_DIR)/usb_android.c
usb_android.$O: $(SRC_TOP)Headers/prologue.h
usb_android.$O: $(BLD_TOP)config.h
usb_android.$O: $(BLD_TOP)forbuild.h
usb_android.$O: $(SRC_TOP)Headers/log.h
usb_android.$O: $(SRC_TOP)Headers/queue.h
usb_android.$O: $(SRC_TOP)Headers/async.h
usb_android.$O: $(SRC_TOP)Headers/async_io.h
usb_android.$O: $(SRC_TOP)Headers/io_usb.h
usb_android.$O: $(SRC_TOP)Headers/serial_types.h
usb_android.$O: $(SRC_TOP)Headers/usb_types.h
usb_android.$O: $(SRC_TOP)Headers/bitfield.h
usb_android.$O: $(SRC_DIR)/usb_internal.h
usb_android.$O: $(SRC_TOP)Headers/common_java.h
usb_android.$O: $(SRC_TOP)Headers/system_java.h

# Dependencies for usb_belkin.$O:
usb_belkin.$O: $(SRC_DIR)/usb_belkin.c
usb_belkin.$O: $(SRC_TOP)Headers/prologue.h
usb_belkin.$O: $(BLD_TOP)config.h
usb_belkin.$O: $(BLD_TOP)forbuild.h
usb_belkin.$O: $(SRC_TOP)Headers/log.h
usb_belkin.$O: $(SRC_TOP)Headers/async.h
usb_belkin.$O: $(SRC_TOP)Headers/async_io.h
usb_belkin.$O: $(SRC_TOP)Headers/io_usb.h
usb_belkin.$O: $(SRC_TOP)Headers/serial_types.h
usb_belkin.$O: $(SRC_TOP)Headers/usb_types.h
usb_belkin.$O: $(SRC_DIR)/usb_serial.h
usb_belkin.$O: $(SRC_DIR)/usb_belkin.h

# Dependencies for usb_cdc_acm.$O:
usb_cdc_acm.$O: $(SRC_DIR)/usb_cdc_acm.c
usb_cdc_acm.$O: $(SRC_TOP)Headers/prologue.h
usb_cdc_acm.$O: $(BLD_TOP)config.h
usb_cdc_acm.$O: $(BLD_TOP)forbuild.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/log.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/strfmt.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/strfmth.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/async.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/async_io.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/io_usb.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/serial_types.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/usb_types.h
usb_cdc_acm.$O: $(SRC_DIR)/usb_serial.h
usb_cdc_acm.$O: $(SRC_DIR)/usb_cdc_acm.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/bitfield.h
usb_cdc_acm.$O: $(SRC_TOP)Headers/queue.h
usb_cdc_acm.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for usb_cp2101.$O:
usb_cp2101.$O: $(SRC_DIR)/usb_cp2101.c
usb_cp2101.$O: $(SRC_TOP)Headers/prologue.h
usb_cp2101.$O: $(BLD_TOP)config.h
usb_cp2101.$O: $(BLD_TOP)forbuild.h
usb_cp2101.$O: $(SRC_TOP)Headers/log.h
usb_cp2101.$O: $(SRC_TOP)Headers/async.h
usb_cp2101.$O: $(SRC_TOP)Headers/async_io.h
usb_cp2101.$O: $(SRC_TOP)Headers/io_usb.h
usb_cp2101.$O: $(SRC_TOP)Headers/serial_types.h
usb_cp2101.$O: $(SRC_TOP)Headers/usb_types.h
usb_cp2101.$O: $(SRC_DIR)/usb_serial.h
usb_cp2101.$O: $(SRC_DIR)/usb_cp2101.h
usb_cp2101.$O: $(SRC_TOP)Headers/bitfield.h

# Dependencies for usb_cp2110.$O:
usb_cp2110.$O: $(SRC_DIR)/usb_cp2110.c
usb_cp2110.$O: $(SRC_TOP)Headers/prologue.h
usb_cp2110.$O: $(BLD_TOP)config.h
usb_cp2110.$O: $(BLD_TOP)forbuild.h
usb_cp2110.$O: $(SRC_TOP)Headers/log.h
usb_cp2110.$O: $(SRC_TOP)Headers/async.h
usb_cp2110.$O: $(SRC_TOP)Headers/async_io.h
usb_cp2110.$O: $(SRC_TOP)Headers/io_usb.h
usb_cp2110.$O: $(SRC_TOP)Headers/serial_types.h
usb_cp2110.$O: $(SRC_TOP)Headers/usb_types.h
usb_cp2110.$O: $(SRC_DIR)/usb_serial.h
usb_cp2110.$O: $(SRC_DIR)/usb_cp2110.h
usb_cp2110.$O: $(SRC_TOP)Headers/bitfield.h

# Dependencies for usb_darwin.$O:
usb_darwin.$O: $(SRC_DIR)/usb_darwin.c
usb_darwin.$O: $(SRC_TOP)Headers/prologue.h
usb_darwin.$O: $(BLD_TOP)config.h
usb_darwin.$O: $(BLD_TOP)forbuild.h
usb_darwin.$O: $(SRC_TOP)Headers/log.h
usb_darwin.$O: $(SRC_TOP)Headers/async.h
usb_darwin.$O: $(SRC_TOP)Headers/async_io.h
usb_darwin.$O: $(SRC_TOP)Headers/io_usb.h
usb_darwin.$O: $(SRC_TOP)Headers/serial_types.h
usb_darwin.$O: $(SRC_TOP)Headers/usb_types.h
usb_darwin.$O: $(SRC_TOP)Headers/bitfield.h
usb_darwin.$O: $(SRC_TOP)Headers/queue.h
usb_darwin.$O: $(SRC_DIR)/usb_internal.h
usb_darwin.$O: $(SRC_TOP)Headers/system_darwin.h

# Dependencies for usb_devices.$O:
usb_devices.$O: $(SRC_DIR)/usb_devices.c
usb_devices.$O: $(SRC_TOP)Headers/prologue.h
usb_devices.$O: $(SRC_DIR)/usb_devices.h
usb_devices.$O: $(BLD_TOP)config.h
usb_devices.$O: $(BLD_TOP)forbuild.h

# Dependencies for usb_freebsd.$O:
usb_freebsd.$O: $(SRC_DIR)/usb_freebsd.c
usb_freebsd.$O: $(SRC_TOP)Headers/prologue.h
usb_freebsd.$O: $(BLD_TOP)config.h
usb_freebsd.$O: $(BLD_TOP)forbuild.h
usb_freebsd.$O: $(SRC_TOP)Headers/async.h
usb_freebsd.$O: $(SRC_TOP)Headers/async_io.h
usb_freebsd.$O: $(SRC_TOP)Headers/io_usb.h
usb_freebsd.$O: $(SRC_TOP)Headers/serial_types.h
usb_freebsd.$O: $(SRC_TOP)Headers/usb_types.h
usb_freebsd.$O: $(SRC_TOP)Headers/bitfield.h
usb_freebsd.$O: $(SRC_TOP)Headers/queue.h
usb_freebsd.$O: $(SRC_DIR)/usb_internal.h
usb_freebsd.$O: $(SRC_TOP)Headers/log.h
usb_freebsd.$O: $(SRC_DIR)/usb_bsd.h

# Dependencies for usb_ftdi.$O:
usb_ftdi.$O: $(SRC_DIR)/usb_ftdi.c
usb_ftdi.$O: $(SRC_TOP)Headers/prologue.h
usb_ftdi.$O: $(BLD_TOP)config.h
usb_ftdi.$O: $(BLD_TOP)forbuild.h
usb_ftdi.$O: $(SRC_TOP)Headers/log.h
usb_ftdi.$O: $(SRC_TOP)Headers/async.h
usb_ftdi.$O: $(SRC_TOP)Headers/async_io.h
usb_ftdi.$O: $(SRC_TOP)Headers/io_usb.h
usb_ftdi.$O: $(SRC_TOP)Headers/serial_types.h
usb_ftdi.$O: $(SRC_TOP)Headers/usb_types.h
usb_ftdi.$O: $(SRC_DIR)/usb_serial.h
usb_ftdi.$O: $(SRC_DIR)/usb_ftdi.h

# Dependencies for usb_grub.$O:
usb_grub.$O: $(SRC_DIR)/usb_grub.c
usb_grub.$O: $(SRC_TOP)Headers/prologue.h
usb_grub.$O: $(BLD_TOP)config.h
usb_grub.$O: $(BLD_TOP)forbuild.h
usb_grub.$O: $(SRC_TOP)Headers/log.h
usb_grub.$O: $(SRC_TOP)Headers/async.h
usb_grub.$O: $(SRC_TOP)Headers/async_io.h
usb_grub.$O: $(SRC_TOP)Headers/io_usb.h
usb_grub.$O: $(SRC_TOP)Headers/serial_types.h
usb_grub.$O: $(SRC_TOP)Headers/usb_types.h
usb_grub.$O: $(SRC_TOP)Headers/bitfield.h
usb_grub.$O: $(SRC_TOP)Headers/queue.h
usb_grub.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for usb_hid.$O:
usb_hid.$O: $(SRC_DIR)/usb_hid.c
usb_hid.$O: $(SRC_TOP)Headers/prologue.h
usb_hid.$O: $(BLD_TOP)config.h
usb_hid.$O: $(BLD_TOP)forbuild.h
usb_hid.$O: $(SRC_TOP)Headers/log.h
usb_hid.$O: $(SRC_TOP)Headers/bitfield.h
usb_hid.$O: $(SRC_TOP)Headers/async.h
usb_hid.$O: $(SRC_TOP)Headers/async_io.h
usb_hid.$O: $(SRC_TOP)Headers/io_usb.h
usb_hid.$O: $(SRC_TOP)Headers/serial_types.h
usb_hid.$O: $(SRC_TOP)Headers/usb_types.h

# Dependencies for usb_kfreebsd.$O:
usb_kfreebsd.$O: $(SRC_DIR)/usb_kfreebsd.c
usb_kfreebsd.$O: $(SRC_TOP)Headers/prologue.h
usb_kfreebsd.$O: $(BLD_TOP)config.h
usb_kfreebsd.$O: $(BLD_TOP)forbuild.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/async.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/async_io.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/io_usb.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/serial_types.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/usb_types.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/bitfield.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/queue.h
usb_kfreebsd.$O: $(SRC_DIR)/usb_internal.h
usb_kfreebsd.$O: $(SRC_TOP)Headers/log.h
usb_kfreebsd.$O: $(SRC_DIR)/usb_bsd.h

# Dependencies for usb_libusb-1.0.$O:
usb_libusb-1.0.$O: $(SRC_DIR)/usb_libusb-1.0.c
usb_libusb-1.0.$O: $(SRC_TOP)Headers/prologue.h
usb_libusb-1.0.$O: $(BLD_TOP)config.h
usb_libusb-1.0.$O: $(BLD_TOP)forbuild.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/log.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/async.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/async_io.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/io_usb.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/serial_types.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/usb_types.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/bitfield.h
usb_libusb-1.0.$O: $(SRC_TOP)Headers/queue.h
usb_libusb-1.0.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for usb_libusb.$O:
usb_libusb.$O: $(SRC_DIR)/usb_libusb.c
usb_libusb.$O: $(SRC_TOP)Headers/prologue.h
usb_libusb.$O: $(BLD_TOP)config.h
usb_libusb.$O: $(BLD_TOP)forbuild.h
usb_libusb.$O: $(SRC_TOP)Headers/log.h
usb_libusb.$O: $(SRC_TOP)Headers/async.h
usb_libusb.$O: $(SRC_TOP)Headers/async_io.h
usb_libusb.$O: $(SRC_TOP)Headers/io_usb.h
usb_libusb.$O: $(SRC_TOP)Headers/serial_types.h
usb_libusb.$O: $(SRC_TOP)Headers/usb_types.h
usb_libusb.$O: $(SRC_TOP)Headers/bitfield.h
usb_libusb.$O: $(SRC_TOP)Headers/queue.h
usb_libusb.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for usb_linux.$O:
usb_linux.$O: $(SRC_DIR)/usb_linux.c
usb_linux.$O: $(SRC_TOP)Headers/prologue.h
usb_linux.$O: $(BLD_TOP)config.h
usb_linux.$O: $(BLD_TOP)forbuild.h
usb_linux.$O: $(SRC_DIR)/parameters.h
usb_linux.$O: $(SRC_TOP)Headers/log.h
usb_linux.$O: $(SRC_TOP)Headers/strfmt.h
usb_linux.$O: $(SRC_TOP)Headers/strfmth.h
usb_linux.$O: $(SRC_TOP)Headers/file.h
usb_linux.$O: $(SRC_TOP)Headers/get_sockets.h
usb_linux.$O: $(SRC_TOP)Headers/parse.h
usb_linux.$O: $(SRC_TOP)Headers/timing.h
usb_linux.$O: $(SRC_TOP)Headers/async.h
usb_linux.$O: $(SRC_TOP)Headers/async_wait.h
usb_linux.$O: $(SRC_TOP)Headers/async_alarm.h
usb_linux.$O: $(SRC_TOP)Headers/async_io.h
usb_linux.$O: $(SRC_TOP)Headers/async_signal.h
usb_linux.$O: $(SRC_TOP)Headers/mntpt.h
usb_linux.$O: $(SRC_TOP)Headers/io_usb.h
usb_linux.$O: $(SRC_TOP)Headers/serial_types.h
usb_linux.$O: $(SRC_TOP)Headers/usb_types.h
usb_linux.$O: $(SRC_TOP)Headers/bitfield.h
usb_linux.$O: $(SRC_TOP)Headers/queue.h
usb_linux.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for usb_netbsd.$O:
usb_netbsd.$O: $(SRC_DIR)/usb_netbsd.c
usb_netbsd.$O: $(SRC_TOP)Headers/prologue.h
usb_netbsd.$O: $(BLD_TOP)config.h
usb_netbsd.$O: $(BLD_TOP)forbuild.h
usb_netbsd.$O: $(SRC_TOP)Headers/async.h
usb_netbsd.$O: $(SRC_TOP)Headers/async_io.h
usb_netbsd.$O: $(SRC_TOP)Headers/io_usb.h
usb_netbsd.$O: $(SRC_TOP)Headers/serial_types.h
usb_netbsd.$O: $(SRC_TOP)Headers/usb_types.h
usb_netbsd.$O: $(SRC_TOP)Headers/bitfield.h
usb_netbsd.$O: $(SRC_TOP)Headers/queue.h
usb_netbsd.$O: $(SRC_DIR)/usb_internal.h
usb_netbsd.$O: $(SRC_TOP)Headers/log.h
usb_netbsd.$O: $(SRC_DIR)/usb_bsd.h

# Dependencies for usb_none.$O:
usb_none.$O: $(SRC_DIR)/usb_none.c
usb_none.$O: $(SRC_TOP)Headers/prologue.h
usb_none.$O: $(BLD_TOP)config.h
usb_none.$O: $(BLD_TOP)forbuild.h
usb_none.$O: $(SRC_TOP)Headers/log.h
usb_none.$O: $(SRC_TOP)Headers/async.h
usb_none.$O: $(SRC_TOP)Headers/async_io.h
usb_none.$O: $(SRC_TOP)Headers/io_usb.h
usb_none.$O: $(SRC_TOP)Headers/serial_types.h
usb_none.$O: $(SRC_TOP)Headers/usb_types.h
usb_none.$O: $(SRC_TOP)Headers/bitfield.h
usb_none.$O: $(SRC_TOP)Headers/queue.h
usb_none.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for usb_openbsd.$O:
usb_openbsd.$O: $(SRC_DIR)/usb_openbsd.c
usb_openbsd.$O: $(SRC_TOP)Headers/prologue.h
usb_openbsd.$O: $(BLD_TOP)config.h
usb_openbsd.$O: $(BLD_TOP)forbuild.h
usb_openbsd.$O: $(SRC_TOP)Headers/async.h
usb_openbsd.$O: $(SRC_TOP)Headers/async_io.h
usb_openbsd.$O: $(SRC_TOP)Headers/io_usb.h
usb_openbsd.$O: $(SRC_TOP)Headers/serial_types.h
usb_openbsd.$O: $(SRC_TOP)Headers/usb_types.h
usb_openbsd.$O: $(SRC_TOP)Headers/bitfield.h
usb_openbsd.$O: $(SRC_TOP)Headers/queue.h
usb_openbsd.$O: $(SRC_DIR)/usb_internal.h
usb_openbsd.$O: $(SRC_TOP)Headers/log.h
usb_openbsd.$O: $(SRC_DIR)/usb_bsd.h

# Dependencies for usb_serial.$O:
usb_serial.$O: $(SRC_DIR)/usb_serial.c
usb_serial.$O: $(SRC_TOP)Headers/prologue.h
usb_serial.$O: $(BLD_TOP)config.h
usb_serial.$O: $(BLD_TOP)forbuild.h
usb_serial.$O: $(SRC_TOP)Headers/log.h
usb_serial.$O: $(SRC_TOP)Headers/pid.h
usb_serial.$O: $(SRC_TOP)Headers/program.h
usb_serial.$O: $(SRC_TOP)Headers/async.h
usb_serial.$O: $(SRC_TOP)Headers/async_io.h
usb_serial.$O: $(SRC_TOP)Headers/io_usb.h
usb_serial.$O: $(SRC_TOP)Headers/serial_types.h
usb_serial.$O: $(SRC_TOP)Headers/usb_types.h
usb_serial.$O: $(SRC_TOP)Headers/bitfield.h
usb_serial.$O: $(SRC_TOP)Headers/queue.h
usb_serial.$O: $(SRC_DIR)/usb_internal.h
usb_serial.$O: $(SRC_DIR)/usb_serial.h
usb_serial.$O: $(SRC_DIR)/usb_adapters.h

# Dependencies for usb_solaris.$O:
usb_solaris.$O: $(SRC_DIR)/usb_solaris.c
usb_solaris.$O: $(SRC_TOP)Headers/prologue.h
usb_solaris.$O: $(BLD_TOP)config.h
usb_solaris.$O: $(BLD_TOP)forbuild.h
usb_solaris.$O: $(SRC_TOP)Headers/log.h
usb_solaris.$O: $(SRC_TOP)Headers/async.h
usb_solaris.$O: $(SRC_TOP)Headers/async_io.h
usb_solaris.$O: $(SRC_TOP)Headers/io_usb.h
usb_solaris.$O: $(SRC_TOP)Headers/serial_types.h
usb_solaris.$O: $(SRC_TOP)Headers/usb_types.h
usb_solaris.$O: $(SRC_TOP)Headers/bitfield.h
usb_solaris.$O: $(SRC_TOP)Headers/queue.h
usb_solaris.$O: $(SRC_DIR)/usb_internal.h

# Dependencies for variables.$O:
variables.$O: $(SRC_DIR)/variables.c
variables.$O: $(SRC_TOP)Headers/prologue.h
variables.$O: $(BLD_TOP)config.h
variables.$O: $(BLD_TOP)forbuild.h
variables.$O: $(SRC_TOP)Headers/log.h
variables.$O: $(SRC_TOP)Headers/strfmt.h
variables.$O: $(SRC_TOP)Headers/strfmth.h
variables.$O: $(SRC_TOP)Headers/variables.h
variables.$O: $(SRC_TOP)Headers/queue.h
variables.$O: $(SRC_TOP)Headers/charset.h
variables.$O: $(SRC_TOP)Headers/lock.h

# Dependencies for xbrlapi.$O:
xbrlapi.$O: $(SRC_DIR)/xbrlapi.c
xbrlapi.$O: $(SRC_TOP)Headers/prologue.h
xbrlapi.$O: $(BLD_TOP)config.h
xbrlapi.$O: $(BLD_TOP)forbuild.h
xbrlapi.$O: brlapi.h
xbrlapi.$O: brlapi_constants.h
xbrlapi.$O: $(SRC_DIR)/brlapi_keycodes.h
xbrlapi.$O: $(SRC_TOP)Headers/datafile.h
xbrlapi.$O: $(SRC_TOP)Headers/options.h
xbrlapi.$O: $(SRC_TOP)Headers/pid.h
xbrlapi.$O: $(SRC_TOP)Headers/program.h
xbrlapi.$O: $(SRC_TOP)Headers/strfmth.h
xbrlapi.$O: $(SRC_TOP)Headers/variables.h

