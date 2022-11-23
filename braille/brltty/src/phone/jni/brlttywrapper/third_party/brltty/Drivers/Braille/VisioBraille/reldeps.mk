# Dependencies for braille.$O:
braille.$O: $(SRC_DIR)/braille.c
braille.$O: $(SRC_TOP)Headers/prologue.h
braille.$O: $(BLD_TOP)config.h
braille.$O: $(BLD_TOP)forbuild.h
braille.$O: $(SRC_TOP)Headers/log.h
braille.$O: $(SRC_TOP)Headers/parse.h
braille.$O: $(SRC_TOP)Headers/driver.h
braille.$O: $(SRC_TOP)Headers/ktb_types.h
braille.$O: $(SRC_TOP)Headers/scr_types.h
braille.$O: $(SRC_TOP)Programs/scr.h
braille.$O: $(SRC_TOP)Headers/message.h
braille.$O: $(SRC_TOP)Headers/api_types.h
braille.$O: $(SRC_TOP)Headers/async.h
braille.$O: $(SRC_TOP)Headers/async_io.h
braille.$O: $(SRC_TOP)Headers/brl_base.h
braille.$O: $(SRC_TOP)Headers/brl_cmds.h
braille.$O: $(SRC_TOP)Headers/brl_dots.h
braille.$O: $(SRC_TOP)Headers/brl_driver.h
braille.$O: $(SRC_TOP)Headers/brl_types.h
braille.$O: $(SRC_TOP)Headers/brl_utils.h
braille.$O: $(SRC_TOP)Headers/cmd_enqueue.h
braille.$O: $(SRC_TOP)Headers/gio_types.h
braille.$O: $(SRC_TOP)Headers/io_generic.h
braille.$O: $(SRC_TOP)Headers/queue.h
braille.$O: $(SRC_TOP)Headers/serial_types.h
braille.$O: $(SRC_TOP)Headers/status_types.h
braille.$O: $(SRC_TOP)Headers/usb_types.h
braille.$O: $(SRC_DIR)/braille.h
braille.$O: $(SRC_DIR)/brldefs-vs.h
braille.$O: $(SRC_TOP)Headers/io_serial.h
braille.$O: $(SRC_DIR)/brl-out.h

# Dependencies for vstp_main.$O:
vstp_main.$O: $(SRC_DIR)/vstp_main.c
vstp_main.$O: $(BLD_TOP)Programs/brlapi.h
vstp_main.$O: $(BLD_TOP)Programs/brlapi_constants.h
vstp_main.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
vstp_main.$O: $(SRC_DIR)/vstp.h

# Dependencies for vstp_transfer.$O:
vstp_transfer.$O: $(SRC_DIR)/vstp_transfer.c
vstp_transfer.$O: $(BLD_TOP)Programs/brlapi.h
vstp_transfer.$O: $(BLD_TOP)Programs/brlapi_constants.h
vstp_transfer.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
vstp_transfer.$O: $(SRC_TOP)Programs/brlapi_protocol.h
vstp_transfer.$O: $(SRC_DIR)/vstp.h

