# Jenkins LTS, as it ships, with the one plugin the release needs: Notification, which posts each build's
# start and finish to the wrapper (the job-finished webhook). No setup wizard: an admin user and the job come from
# init.groovy.d and jobs/.
FROM jenkins/jenkins:lts-jdk21
ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION=true"
# Notification reads test results through the JUnit plugin's classes and expands its messages with Token Macro, so
# it needs both beside it.
RUN jenkins-plugin-cli --plugins notification junit token-macro
COPY --chown=jenkins:jenkins init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
COPY --chown=jenkins:jenkins jobs/ /usr/share/jenkins/ref/jobs/
