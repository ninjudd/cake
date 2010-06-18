Cake is a build tool for Clojure that is as easy to use as it sounds.

Cake is inspired by many fond memories of Rake and countless hours of singeing my hair
with other Java and Clojure build tools.

## Installation

There are three easy ways to get cake. The simplest method is just to install the gem. If
you're new, that's what we recommend.

### Using gem

    gem install cake

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