# Dependencies for eu_braille.$O:
eu_braille.$O: $(SRC_DIR)/eu_braille.c
eu_braille.$O: $(SRC_TOP)Headers/prologue.h
eu_braille.$O: $(BLD_TOP)config.h
eu_braille.$O: $(BLD_TOP)forbuild.h
eu_braille.$O: $(SRC_TOP)Headers/message.h
eu_braille.$O: $(SRC_TOP)Headers/log.h
eu_braille.$O: $(SRC_TOP)Headers/api_types.h
eu_braille.$O: $(SRC_TOP)Headers/async.h
eu_braille.$O: $(SRC_TOP)Headers/async_io.h
eu_braille.$O: $(SRC_TOP)Headers/brl_base.h
eu_braille.$O: $(SRC_TOP)Headers/brl_cmds.h
eu_braille.$O: $(SRC_TOP)Headers/brl_dots.h
eu_braille.$O: $(SRC_TOP)Headers/brl_driver.h
eu_braille.$O: $(SRC_TOP)Headers/brl_types.h
eu_braille.$O: $(SRC_TOP)Headers/brl_utils.h
eu_braille.$O: $(SRC_TOP)Headers/cmd_enqueue.h
eu_braille.$O: $(SRC_TOP)Headers/driver.h
eu_braille.$O: $(SRC_TOP)Headers/gio_types.h
eu_braille.$O: $(SRC_TOP)Headers/io_generic.h
eu_braille.$O: $(SRC_TOP)Headers/ktb_types.h
eu_braille.$O: $(SRC_TOP)Headers/queue.h
eu_braille.$O: $(SRC_TOP)Headers/serial_types.h
eu_braille.$O: $(SRC_TOP)Headers/status_types.h
eu_braille.$O: $(SRC_TOP)Headers/usb_types.h
eu_braille.$O: $(SRC_TOP)Headers/parse.h
eu_braille.$O: $(SRC_TOP)Headers/async_wait.h

# Dependencies for eu_clio.$O:
eu_clio.$O: $(SRC_DIR)/eu_clio.c
eu_clio.$O: $(SRC_TOP)Headers/prologue.h
eu_clio.$O: $(BLD_TOP)config.h
eu_clio.$O: $(BLD_TOP)forbuild.h
eu_clio.$O: $(SRC_TOP)Headers/log.h
eu_clio.$O: $(SRC_TOP)Headers/timing.h
eu_clio.$O: $(SRC_TOP)Headers/ascii.h
eu_clio.$O: $(SRC_DIR)/brldefs-eu.h
eu_clio.$O: $(SRC_DIR)/eu_protocol.h
eu_clio.$O: $(SRC_TOP)Headers/api_types.h
eu_clio.$O: $(SRC_TOP)Headers/async.h
eu_clio.$O: $(SRC_TOP)Headers/brl_base.h
eu_clio.$O: $(SRC_TOP)Headers/brl_cmds.h
eu_clio.$O: $(SRC_TOP)Headers/brl_dots.h
eu_clio.$O: $(SRC_TOP)Headers/brl_types.h
eu_clio.$O: $(SRC_TOP)Headers/brl_utils.h
eu_clio.$O: $(SRC_TOP)Headers/cmd_enqueue.h
eu_clio.$O: $(SRC_TOP)Headers/driver.h
eu_clio.$O: $(SRC_TOP)Headers/gio_types.h
eu_clio.$O: $(SRC_TOP)Headers/ktb_types.h
eu_clio.$O: $(SRC_TOP)Headers/queue.h
eu_clio.$O: $(SRC_TOP)Headers/serial_types.h
eu_clio.$O: $(SRC_TOP)Headers/usb_types.h

# Dependencies for eu_esysiris.$O:
eu_esysiris.$O: $(SRC_DIR)/eu_esysiris.c
eu_esysiris.$O: $(SRC_TOP)Headers/prologue.h
eu_esysiris.$O: $(BLD_TOP)config.h
eu_esysiris.$O: $(BLD_TOP)forbuild.h
eu_esysiris.$O: $(SRC_TOP)Headers/log.h
eu_esysiris.$O: $(SRC_TOP)Headers/ascii.h
eu_esysiris.$O: $(SRC_DIR)/brldefs-eu.h
eu_esysiris.$O: $(SRC_DIR)/eu_protocol.h
eu_esysiris.$O: $(SRC_TOP)Headers/api_types.h
eu_esysiris.$O: $(SRC_TOP)Headers/async.h
eu_esysiris.$O: $(SRC_TOP)Headers/brl_base.h
eu_esysiris.$O: $(SRC_TOP)Headers/brl_cmds.h
eu_esysiris.$O: $(SRC_TOP)Headers/brl_dots.h
eu_esysiris.$O: $(SRC_TOP)Headers/brl_types.h
eu_esysiris.$O: $(SRC_TOP)Headers/brl_utils.h
eu_esysiris.$O: $(SRC_TOP)Headers/cmd_enqueue.h
eu_esysiris.$O: $(SRC_TOP)Headers/driver.h
eu_esysiris.$O: $(SRC_TOP)Headers/gio_types.h
eu_esysiris.$O: $(SRC_TOP)Headers/ktb_types.h
eu_esysiris.$O: $(SRC_TOP)Headers/queue.h
eu_esysiris.$O: $(SRC_TOP)Headers/serial_types.h
eu_esysiris.$O: $(SRC_TOP)Headers/usb_types.h
eu_esysiris.$O: $(SRC_DIR)/eu_protocoldef.h

# Dependencies for eutp_brl.$O:
eutp_brl.$O: $(SRC_DIR)/eutp_brl.c
eutp_brl.$O: $(BLD_TOP)Programs/brlapi.h
eutp_brl.$O: $(BLD_TOP)Programs/brlapi_constants.h
eutp_brl.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
eutp_brl.$O: $(SRC_DIR)/eutp_brl.h
eutp_brl.$O: $(SRC_DIR)/eutp_pc.h
eutp_brl.$O: $(SRC_DIR)/eutp_tools.h
eutp_brl.$O: $(SRC_DIR)/eutp_transfer.h

# Dependencies for eutp_convert.$O:
eutp_convert.$O: $(SRC_DIR)/eutp_convert.c
eutp_convert.$O: $(BLD_TOP)Programs/brlapi.h
eutp_convert.$O: $(BLD_TOP)Programs/brlapi_constants.h
eutp_convert.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
eutp_convert.$O: $(SRC_DIR)/eutp_brl.h

# Dependencies for eutp_debug.$O:
eutp_debug.$O: $(SRC_DIR)/eutp_debug.c

# Dependencies for eutp_main.$O:
eutp_main.$O: $(SRC_DIR)/eutp_main.c
eutp_main.$O: $(SRC_DIR)/eutp_brl.h
eutp_main.$O: $(BLD_TOP)Programs/brlapi.h
eutp_main.$O: $(BLD_TOP)Programs/brlapi_constants.h
eutp_main.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
eutp_main.$O: $(SRC_DIR)/eutp_convert.h
eutp_main.$O: $(SRC_DIR)/eutp_pc.h

# Dependencies for eutp_pc.$O:
eutp_pc.$O: $(SRC_DIR)/eutp_pc.c
eutp_pc.$O: $(SRC_DIR)/eutp_brl.h
eutp_pc.$O: $(BLD_TOP)Programs/brlapi.h
eutp_pc.$O: $(BLD_TOP)Programs/brlapi_constants.h
eutp_pc.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
eutp_pc.$O: $(SRC_DIR)/eutp_pc.h

# Dependencies for eutp_tools.$O:
eutp_tools.$O: $(SRC_DIR)/eutp_tools.c
eutp_tools.$O: $(SRC_DIR)/eutp_brl.h
eutp_tools.$O: $(BLD_TOP)Programs/brlapi.h
eutp_tools.$O: $(BLD_TOP)Programs/brlapi_constants.h
eutp_tools.$O: $(SRC_TOP)Programs/brlapi_keycodes.h

# Dependencies for eutp_transfer.$O:
eutp_transfer.$O: $(SRC_DIR)/eutp_transfer.c
eutp_transfer.$O: $(BLD_TOP)Programs/brlapi.h
eutp_transfer.$O: $(BLD_TOP)Programs/brlapi_constants.h
eutp_transfer.$O: $(SRC_TOP)Programs/brlapi_keycodes.h
eutp_transfer.$O: $(SRC_DIR)/eutp_brl.h
eutp_transfer.$O: $(SRC_DIR)/eutp_debug.h
eutp_transfer.$O: $(SRC_DIR)/eutp_tools.h
eutp_transfer.$O: $(SRC_DIR)/eutp_convert.h

