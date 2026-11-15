# Jenkins LTS, as it ships, with the one plugin the release needs: Notification, which posts each build's
# start and finish to the wrapper (the job-finished webhook). No setup wizard: an admin user and the job come from
# init.groovy.d and jobs/.
FROM jenkins/jenkins:lts-jdk21
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION=true"
# Notification reads test results through the JUnit plugin's classes and expands its messages with Token Macro, so
# it needs both beside it.
RUN jenkins-plugin-cli --plugins notification junit token-macro
# The library app is mounted from the host read-only at /repos. On Linux its files belong to the host user, not to
# jenkins, and git refuses a repository another user owns ("dubious ownership"); Docker Desktop on a Mac hides
# that. Every repository mounted there is trusted.
USER root
RUN git config --system --add safe.directory "*"
# The reference files stay root's, readable but not writable by jenkins: at start Jenkins copies them into
# JENKINS_HOME as its own, where it may change them.
COPY init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
COPY jobs/ /usr/share/jenkins/ref/jobs/
# run.sh writes the job's config.xml before building. The kit (kit/release) builds from this folder as it is and
# names the hook here instead.
ARG HOOK_URL=
RUN app=/usr/share/jenkins/ref/jobs/app; \
    if [ -n "$HOOK_URL" ] && [ ! -f "$app/config.xml" ]; then \
      sed "s#@@HOOK_URL@@#$HOOK_URL#" "$app/config.xml.template" > "$app/config.xml"; \
    fi
USER jenkins
