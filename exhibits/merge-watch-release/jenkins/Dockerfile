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
USER jenkins
COPY --chown=jenkins:jenkins init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
COPY --chown=jenkins:jenkins jobs/ /usr/share/jenkins/ref/jobs/
