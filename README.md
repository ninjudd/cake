Cake is a build tool for Clojure that is as easy to use as it sounds.

Cake is inspired by many fond memories of Rake and countless hours of singeing my hair
with other Java and Clojure build tools.

## Installation

There are three easy ways to get cake. The simplest method is just to install the gem. If
you're new, that's what we recommend.

### Using gem

1. `gem install cake`

### Standalone script

1. [Download the script](https://github.com/ninjudd/cake/raw/master/bin/cake)
2. Put it somewhere in your path and `chmod +x cake` to make it executable

### Git repository

1. `git clone http://github.com/ninjudd/cake`
2. Symlink bin/cake into your path and make it executable

## Getting Started

Cake is compatible with Leiningen project.clj files, so if you already have a project.clj,
you're ready to go. Just install cake and then type `cake` in your project root for a list
of tasks.

If you don't yet have a project.clj file, creating one is simple. Here's an example:

    (defproject jiraph "0.2.7"
      :description "Embedded graph db library for Clojure."
      :url "http://jiraph.org"
      :tasks [protobuf.tasks]
      :dependencies [[clojure "1.2.0-master-SNAPSHOT"]
                     [clojure-contrib "1.2.0-SNAPSHOT"]
                     [clojure-useful "0.2.1"]
                     [clojure-protobuf "0.3.0"]
                     [tokyocabinet "1.2.3"]])

## Default Tasks

Cake provides default tasks for most of the things you probably do on a regular basis.

    cake compile  ;; Compile all clojure and java source files.
    cake deps     ;; Fetch dependencies and create pom.xml.
    cake help     ;; Print tasks with documentation (use -a for all tasks).
    cake install  ;; Install jar to local repository.
    cake jar      ;; Build a jar file containing project source and class files.
    cake release  ;; Release project jar to clojars and gem package to rubygems.
    cake repl     ;; Start an interactive shell.
    cake swank    ;; Report status of swank server and start it if not running.
    cake test     ;; Run project tests.
    cake uberjar  ;; Create a standalone jar containing all project dependencies.
    cake uberwar  ;; Create a web archive containing all project dependencies.
    cake war      ;; Create a web archive containing project source and class files.

## Custom Tasks

You can also create your own custom tasks using the `deftask` macro. You can add your
tasks directly to project.clj or build.clj, or if you put your tasks in a namespace within
your src directory they can be used by both your project and other projects. In this case,
you just need to add the enclosing namespace to the `:tasks` vector in project.clj.

Like many build tools, cake uses a dependency-based programming style. This means you
specify the tasks your task depends on and cake will run those tasks before running your
task, ensuring that each task is only run once. For more details, check out Martin Fowler's
[excellent article](http://martinfowler.com/articles/rake.html#DependencyBasedProgramming)
on Rake. Here is the example from that article using cake syntax:

    (deftask code-gen
      "This task generates code. It has no dependencies."
      (println "generating code...")
      ...)

    (deftask compile => code-gen
      "This task does the compilation. It depends on code-gen."
      (println "compiling...")
      ...)

    (deftask data-load => code-gen
      "This task loads the test data. It depends on code-gen."
      (println "loading test data...")
      ...)

    (deftask test => compile, data-load
      "This task runs the tests. It depends on compile and data-load."
      (println "running tests...")
      ...)
