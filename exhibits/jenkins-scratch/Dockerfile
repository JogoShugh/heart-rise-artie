FROM jenkins/jenkins:lts-jdk21
ENV JAVA_OPTS=-Djenkins.install.runSetupWizard=false
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc.yaml
RUN jenkins-plugin-cli --plugins configuration-as-code job-dsl workflow-aggregator cloudbees-folder
COPY casc.yaml /var/jenkins_home/casc.yaml
