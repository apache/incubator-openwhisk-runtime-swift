#!/usr/bin/env python3
"""Swift Action Compiler
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""

from __future__ import print_function
import os
import re
import sys
import codecs
import subprocess
from io import StringIO


def eprint(*args, **kwargs):
    print(*args, file=sys.stderr, **kwargs)


def sources(launcher, source_dir, main):
    actiondir = "%s/Sources" % source_dir
    # copy the launcher fixing the main
    dst = "%s/main.swift" % actiondir
    with codecs.open(dst, "a", "utf-8") as d:
        with codecs.open(launcher, "r", "utf-8") as e:
            code = e.read()
            code += "while let inputStr: String = readLine() {\n"
            code += "  let json = inputStr.data(using: .utf8, allowLossyConversion: true)!\n"
            code += "  let parsed = try JSONSerialization.jsonObject(with: json, options: []) as! [String: Any]\n"
            code += "  for (key, value) in parsed {\n"
            code += '    if key != "value" {\n'
            code += '      setenv("__OW_\\(key.uppercased())",value as! String,1)\n'
            code += "    }\n"
            code += "  }\n"
            code += '  let jsonData = try JSONSerialization.data(withJSONObject: parsed["value"] as Any, options: [])\n'
            code += "  _run_main(mainFunction: %s, json: jsonData)\n" % main
            code += "} \n"
            d.write(code)


def swift_build(dir, buildcmd):
    # compile...
    env = {"PATH": os.environ["PATH"]}
    p = subprocess.Popen(
        buildcmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=dir, env=env
    )
    (o, e) = p.communicate()
    # stdout/stderr may be either text or bytes, depending on Python
    # version, so if bytes, decode to text. Note that in Python 2
    # a string will match both types; so also skip decoding in that case
    if isinstance(o, bytes) and not isinstance(o, str):
        o = o.decode("utf-8")
    if isinstance(e, bytes) and not isinstance(e, str):
        e = e.decode("utf-8")
    return p.returncode, o, e


def build(source_dir, target_file, buildcmd):
    r, o, e = swift_build(source_dir, buildcmd)
    # if e: print(e)
    # if o: print(o)
    if r != 0:
        print(e)
        print(o)
        print(r)
        return

    bin_file = "%s/.build/release/Action" % source_dir
    os.rename(bin_file, target_file)
    if not os.path.isfile(target_file):
        print("failed %s -> %s" % (bin_file, target_file))
        return


def main(argv):
    if len(argv) < 4:
        print("usage: <main-function> <source-dir> <target-dir>")
        sys.exit(1)

    main = argv[1]
    source_dir = os.path.abspath(argv[2])
    target = os.path.abspath("%s/exec" % argv[3])
    launch = os.path.abspath(argv[0] + ".launcher.swift")

    src = "%s/exec" % source_dir

    # check if single source
    if os.path.isfile(src):
        actiondir = os.path.abspath("Sources")
        if not os.path.isdir(actiondir):
            os.makedirs(actiondir, mode=0o755)
        dst = "%s/main.swift" % actiondir
        os.rename(src, dst)
        sources(launch, os.path.abspath("."), main)
        build(os.path.abspath("."), target, ["./swiftbuildandlink.sh"])
    else:
        actiondir = "%s/Sources" % source_dir
        if not os.path.isdir(actiondir):
            os.makedirs(actiondir, mode=0o755)
        os.rename(
            os.path.abspath("Sources/_Whisk.swift"),
            "%s/Sources/_Whisk.swift" % source_dir,
        )
        sources(launch, source_dir, main)
        build(source_dir, target, ["swift", "build", "-c", "release"])


if __name__ == "__main__":
    main(sys.argv)
