# Copyright 2022 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# This is a replacement for brltty's braille.mk, defining some make variables
# that the driver Makefiles depend on.

# In brltty, this is the object file extension.  We define this to be a unique
# placeholder per driver so that the make rules in the individual makefiles
# won't interfere with the android build system or each other.
O := NOTUSED$(DRIVER_CODE)
