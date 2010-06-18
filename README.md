Cake is a build tool for Clojure that is as easy to use as it sounds.

Cake is inspired by many fond memories of Rake and countless hours of "setting my
hair on fire" with other Clojure build tools.

## Default Tasks

Cake provides default tasks for most of the things you probably do on a regular basis.

    cake compile  ;; Compile all clojure and java source files.
    cake deps     ;; Fetch dependencies and create pom.xml.
    cake gem      ;; Build standalone gem package.
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

## Installation

There are three easy ways to get started with cake.

### Using gem

    gem install cake

### Standalone script

1. [Download script](https://github.com/ninjudd/cake/raw/master/bin/cake)
2. Put it somewhere in your path and `chmod +x cake` to make it executable

### Git repository

1. `git clone http://github.com/ninjudd/cake`
2. Symlink bin/cake into your path and make it executable