#!/usr/bin/env ruby
# Regenerates iosApp.xcodeproj. Needs the xcodeproj gem:  gem install xcodeproj
#   cd mobile/iosApp && ruby generate_project.rb
require 'xcodeproj'

Dir.chdir(__dir__)
FileUtils.rm_rf('iosApp.xcodeproj')
project = Xcodeproj::Project.new('iosApp.xcodeproj')
project.root_object.attributes['LastSwiftUpdateCheck'] = '1600'

target = project.new_target(:application, 'iosApp', :ios, '16.0')
target.product_reference.name = 'handsup.app'

group = project.main_group.new_group('iosApp', 'iosApp')
%w[iOSApp.swift ContentView.swift].each { |f| target.add_file_references([group.new_file(f)]) }
group.new_file('Info.plist')
assets = group.new_file('Assets.xcassets')
target.resources_build_phase.add_file_reference(assets)
config_ref = project.main_group.new_file('Config.xcconfig')

# Firebase through Swift Package Manager (GitLive's Kotlin SDK needs the native SDK on iOS).
package = project.new(Xcodeproj::Project::Object::XCRemoteSwiftPackageReference)
package.repositoryURL = 'https://github.com/firebase/firebase-ios-sdk'
package.requirement = { 'kind' => 'upToNextMajorVersion', 'minimumVersion' => '11.8.0' }
project.root_object.package_references << package
%w[FirebaseCore FirebaseAuth FirebaseFirestore].each do |product|
  dep = project.new(Xcodeproj::Project::Object::XCSwiftPackageProductDependency)
  dep.package = package
  dep.product_name = product
  target.package_product_dependencies << dep
  build_file = project.new(Xcodeproj::Project::Object::PBXBuildFile)
  build_file.product_ref = dep
  target.frameworks_build_phase.files << build_file
end

# Build the Kotlin framework before compiling Swift.
script = target.new_shell_script_build_phase('Build Kotlin framework')
script.shell_script = <<~SH
  set -e
  if [ "YES" = "$OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED" ]; then
    echo "Skipping Gradle build: running from the IDE"
    exit 0
  fi
  if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
    export JAVA_HOME="$(/usr/libexec/java_home 2>/dev/null || true)"
  fi
  cd "$SRCROOT/.."
  ./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
SH
script.always_out_of_date = '1'
target.build_phases.move(script, 0)

target.build_configurations.each do |config|
  config.base_configuration_reference = config_ref
  s = config.build_settings
  s['PRODUCT_BUNDLE_IDENTIFIER'] = 'es.gdgaranjuez.handsup'
  s['PRODUCT_NAME'] = 'handsup'
  s['INFOPLIST_FILE'] = 'iosApp/Info.plist'
  s['ASSETCATALOG_COMPILER_APPICON_NAME'] = 'AppIcon'
  s['SWIFT_VERSION'] = '5.0'
  s['TARGETED_DEVICE_FAMILY'] = '1,2'
  s['IPHONEOS_DEPLOYMENT_TARGET'] = '16.0'
  s['MARKETING_VERSION'] = '0.1.0'
  s['CURRENT_PROJECT_VERSION'] = '1'
  s['GENERATE_INFOPLIST_FILE'] = 'NO'
  s['ENABLE_USER_SCRIPT_SANDBOXING'] = 'NO'
  s['CODE_SIGN_STYLE'] = 'Automatic'
  s['DEVELOPMENT_TEAM'] = '$(HANDSUP_TEAM_ID)'
  s['FRAMEWORK_SEARCH_PATHS'] = ['$(inherited)', '$(SRCROOT)/../composeApp/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)']
  s['OTHER_LDFLAGS'] = ['$(inherited)', '-ObjC', '-framework', 'ComposeApp']
  s['LD_RUNPATH_SEARCH_PATHS'] = ['$(inherited)', '@executable_path/Frameworks']
end
project.build_configurations.each do |config|
  config.build_settings['SDKROOT'] = 'iphoneos'
  config.build_settings['ONLY_ACTIVE_ARCH'] = 'YES' if config.name == 'Debug'
end

project.save

scheme = Xcodeproj::XCScheme.new
scheme.add_build_target(target)
scheme.set_launch_target(target)
scheme.save_as(project.path, 'iosApp', true)
puts 'wrote iosApp.xcodeproj'
