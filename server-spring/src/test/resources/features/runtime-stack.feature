Feature: The server ships the web stack its tests run
  The Cucumber suite runs the controllers in a WebFlux slice. Production
  must run WebFlux too, or the fast tests exercise framework code
  (conditional GET, negotiation, body reading, error mapping) that never
  ships. spring-boot-starter-hateoas brings spring-boot-starter-web
  (Spring MVC on Tomcat), and with both on the classpath Spring Boot picks
  the servlet stack, so that dependency has to stay out.

  Scenario: The application starts as a reactive application, not a servlet one
    Then the server application's web stack is "REACTIVE"
    And no servlet container is on the runtime classpath
