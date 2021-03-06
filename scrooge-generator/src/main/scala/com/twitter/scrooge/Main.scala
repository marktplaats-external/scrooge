/*
 * Copyright 2011 Twitter, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twitter.scrooge

import java.io.File
import java.util.Properties
import scopt.OptionParser
import com.twitter.scrooge.backend.{WithOstrichServer, WithFinagleClient, WithFinagleService, WithSkipNullWrite}

object Main {
  import Language._

  def main(args: Array[String]) {
    val compiler = new Compiler()
    if (!parseOptions(compiler, args)) {
      System.exit(1)
    }
    compiler.run()
  }

  def parseOptions(compiler: Compiler, args: Seq[String]): Boolean = {
    val buildProperties = new Properties
    Option(getClass.getResource("build.properties")) foreach { resource =>
      buildProperties.load(resource.openStream)
    }

    val parser = new OptionParser("scrooge") {
      help(None, "help", "show this help screen")
      opt("V", "version", "print version and quit", {
        println("scrooge " + buildProperties.getProperty("version", "0.0"))
        println("    build " + buildProperties.getProperty("build_name", "unknown"))
        println("    git revision " + buildProperties.getProperty("build_revision", "unknown"))
        System.exit(0)
        ()
      })
      opt("v", "verbose", "log verbose messages about progress", { compiler.verbose = true; () })
      opt("d", "dest", "<path>",
      "write generated code to a folder (default: %s)".format(compiler.defaultDestFolder), { x: String =>
        compiler.destFolder = x
      })
      opt("i", "import-path", "<path>", "path(s) to search for imported thrift files (may be used multiple times)", { path: String =>
        compiler.importPaths ++= path.split(File.pathSeparator); ()
      })
      opt("n", "namespace-map", "<oldname>=<newname>", "map old namespace to new (may be used multiple times)", { mapping: String =>
        mapping.split("=") match {
          case Array(from, to) => compiler.namespaceMappings(from) = to
        }
        ()
      })
      opt(None, "default-java-namespace", "<name>",
      "Use <name> as default namespace if the thrift file doesn't define its own namespace. " +
        "If this option is not specified either, then use \"thrift\" as default namespace",
      { name: String => compiler.defaultNamespace = name }
      )
      opt("disable-strict", "issue warnings on non-severe parse errors instead of aborting",
      { compiler.strict = false; () })
      opt(None, "gen-file-map", "<path>", "generate map.txt in the destination folder to specify the mapping from input thrift files to output Scala/Java files", { path: String =>
        compiler.fileMapPath = Some(path)
        ()
      })
      opt("dry-run",
      "parses and validates source thrift files, reporting any errors, but" +
        " does not emit any generated source code.  can be used with " +
        "--gen-file-mapping to get the file mapping",
      { compiler.dryRun = true; () }
      )
      opt("s", "skip-unchanged", "Don't re-generate if the target is newer than the input", { compiler.skipUnchanged = true; () })
      opt("l", "language", "name of language to generate code in ('java' and 'scala' are currently supported)", { languageString: String =>
        languageString.toLowerCase match {
          case "scala" => compiler.language = Scala
          case "java" => compiler.language = Java
          case _ =>
            println("language option %s not supported".format(languageString))
            System.exit(0)
        }
        ()
      })

      opt("finagle", "generate finagle classes", {
        compiler.flags += WithFinagleService
        compiler.flags += WithFinagleClient
        ()
      })
      opt("ostrich", "generate ostrich server interface", { compiler.flags += WithOstrichServer; () })
      opt("allowNull", "allow null as value for fields (while writing) regardless of field's requiredness", {
        compiler.flags += WithSkipNullWrite
        ()
      })
      arglist("<files...>", "thrift files to compile", { compiler.thriftFiles += _ })
    }
    parser.parse(args)
  }

  def isUnchanged(file: File, sourceLastModified: Long): Boolean = {
    file.exists && file.lastModified >= sourceLastModified
  }
}
