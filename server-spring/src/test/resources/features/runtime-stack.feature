Feature: The server ships the web stack its tests run
  The Cucumber suite runs the HEART library in a WebFlux slice. Production
  must run WebFlux too, or the fast tests exercise framework code
  (conditional GET, negotiation, body reading, error mapping) that never
  ships. With Spring MVC on the classpath beside WebFlux, Spring Boot picks
  the servlet stack, so no dependency may bring it in.

  Scenario: The application starts as a reactive application, not a servlet one
    Then the server application's web stack is "REACTIVE"
    And no servlet container is on the runtime classpath
