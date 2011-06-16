Cake is a tasty build tool for clojure, designed to be as powerful and fun to use as clojure itself.
Cake has the following features:

* persistent JVM, eliminating start-up overhead
* dependency based task model, making it simple to define your own tasks and extend existing ones
* large library of built in tasks
* compatibile with most Leiningen project.clj files
* cross platform (Linux, OS X, Windows, Commodore 64)
* enhanced concurrent REPL with command history, paren matching and tab completion

_This software is a pre-release, please submit an issue on github if you run into any problems._

## Installation

There are three ways to get Cake. The simplest method is just to install the gem. If you're new,
that's what we recommend.

### Using gem

1. `gem install cake`

### Standalone script

1. [Download the script](http://releases.clojure-cake.org/cake)
2. Put it somewhere in your path and `chmod +x cake` to make it executable

### Git repository

1. `git clone git://github.com/ninjudd/cake.git`
2. Symlink bin/cake into your path and make it executable

Note: some users have reported problems with clojure and java 1.5, so you may want to make sure
you're on 1.6 with `java -version` if you are having problems. On OS X, you can switch to java 1.6
using `/Applications/Utilities/Java Preferences.app`.

## Getting Started

Cake is compatible with Leiningen project.clj files, so if you already have a project.clj,
you're ready to go. Just install Cake and then type `cake` in your project root for a list
of tasks.

If you don't yet have a project.clj file, creating one is simple. Here's an example:

    (defproject jiraph "0.2.7"
      :description "Embedded graph db library for Clojure."
      :url "http://jiraph.org"
      :tasks [protobuf.tasks]
      :dependencies [[clojure "1.2.0"]
                     [clojure-contrib "1.2.0"]
                     [clojure-useful "0.2.1"]
                     [clojure-protobuf "0.3.0"]
                     [tokyocabinet "1.2.3"]])

## Default Tasks

Cake provides default tasks for most of the things you probably do on a regular basis.

    cake help      ;; Print tasks with documentation. Use 'cake help TASK' for more details.
    cake deps      ;; Fetch dependencies and create pom.xml.
    cake clean     ;; Remove cake build artifacts.
    cake repl      ;; Start an interactive shell with history and tab completion.
    cake run       ;; Execute a script in the project jvm.
    cake test      ;; Run project tests.
    cake autotest  ;; Automatically run tests whenever your project code changes.
    cake compile   ;; Compile all clojure and java source files.
    cake jar       ;; Build a jar file containing project source and class files.
    cake uberjar   ;; Create a standalone jar containing all project dependencies.
    cake bin       ;; Create a standalone console executable for your project.
    cake install   ;; Install jar to local repository.
    cake release   ;; Release project jar to clojars.
    cake deploy    ;; Deploy war to a group of servers.
    cake upgrade    ;; Upgrade cake to the most current version.
    cake eval      ;; Eval the given forms in the project JVM.
    cake filter    ;; Thread each stdin line through the given forms, printing the results.
    cake war       ;; Create a web archive containing project source and class files.
    cake uberwar   ;; Create a web archive containing all project dependencies.
    cake swank     ;; Report status of swank server and start it if not running.
    cake version   ;; Print the current project name and version.

Cake also provides several system tasks for managing the persistent JVM.

    cake ps        ;; List running cake jvm processes for all projects.
    cake kill      ;; Kill running cake jvm processes. Use -9 to force.
    cake killall   ;; Kill all running cake jvm processes for all projects.
    cake log       ;; Tail the cake log file. Optionally pass the number of lines of history to show.

[Default Task Documentation](http://wiki.github.com/ninjudd/cake/default-tasks)

## Documentation

There is extensive documentation on creating and working with tasks on the 
[wiki](http://github.com/ninjudd/cake/wiki), along with documentation on most everything you'll ever need to know
to use and work with cake.

## A Persistent JVM

If you've used the JVM for much time at all, you know that one of the worst things about
it is the incredibly slow start-up time. This is even more apparent when you are running a
build tool. Cake solves this problem by using persistent JVMs (one for Cake itself, and
one for your project). When you run the `cake` script, it first makes sure the JVM is
running, then it connects using a socket and sends your command. This makes interacting
with your Clojure project blazingly fast. This also makes it really easy to open multiple
REPL threads that all share a single JVM which is great for testing parallel code.

Cake tries to keep the persistent JVMs running as long as possible by reloading Clojure
files that have changed. However, when .java, .class and .jar files change, Cake has to
restart the project JVM. If you have existing REPL or Swank connections though, Cake will
refuse to close the JVM, printing a warning instead.

## Contributors

- Justin Balthrop ([ninjudd](https://github.com/ninjudd))
- Lance Bradley ([lancepantz](https://github.com/lancepantz))
- Anthony Grimes ([Raynes](https://github.com/Raynes))
- Luke Renn ([lrenn](https://github.com/lrenn))
- David Santiago ([davidsantiago](https://github.com/davidsantiago))
- Alan Malloy ([amalloy](https://github.com/amalloy))
- Jeff Rose ([rosejn](https://github.com/rosejn))
- Martin Sander ([marvinthepa](https://github.com/marvinthepa))


## YourKit

YourKit's Java Profiler was a terrific help to us in finding classloader memory leaks when we
switched Cake to use a single JVM with a separate project classloader.

YourKit is kindly supporting open source projects with its full-featured Java Profiler.
YourKit, LLC is the creator of innovative and intelligent tools for profiling
Java and .NET applications. Take a look at YourKit's leading software products:
[YourKit Java Profiler](http://www.yourkit.com/java/profiler/index.jsp) and
[YourKit .NET Profiler](http://www.yourkit.com/.net/profiler/index.jsp).
