Gem::Specification.new do |s|
  s.name = 'cake'
  s.rubyforge_project = 'cake'
  s.version = ENV['CAKE_VERSION']
  s.authors = ['Justin Balthrop', 'Lance Bradley']
  s.date = Time.now
  s.default_executable = 'cake'
  s.summary = 'The cake stands alone.'
  s.description = "Save your fork, there's cake!"
  s.email = 'code@justinbalthrop.com'
  s.executables = ['cake']
  s.files = ['cake.gemspec', 'bin/cake', 'lib/cake.jar', 'lib/bake.jar']
  s.homepage = 'http://github.com/ninjudd/cake'
end
