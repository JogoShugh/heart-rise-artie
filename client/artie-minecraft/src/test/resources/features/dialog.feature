Feature: A HEART resource as a Minecraft dialog
  A Minecraft dialog knows buttons and inputs, as a browser knows links and
  forms. ARTIE reads the resource, and the dialog shows what the
  representation holds: its state as lines of text, a button for each act on
  offer, a line for each act that is absent and why, and each act's form as a
  dialog of inputs. A dialog cannot change while it is open, so a property
  whose choices depend on another field is asked for after that field. The
  choices are only the ones on offer. An act is sent with If-Match, and its
  answer is the next dialog; a refusal is shown in the server's words. Here a
  player opens the deploy service's app "web".

  Background:
    Given the deploy service's app "web" has the regions
      | environment | name    | label   | available |
      | staging     | us-east | US East | true      |
      | staging     | eu-west | EU West | false     |
      | production  | us-east | US East | true      |
    And the deploy service has the releases
      | app | version | status    |
      | web | 1.0     | succeeded |
      | web | 1.1     | succeeded |
      | web | 1.2     | failed    |
    When a player opens the app "web"

  Scenario: The app, idle: its state, a button for each act on offer, and why the other act is absent
    Then the dialog is titled "web"
    And its body reads
      | id: 1                                                |
      | name: web                                            |
      | status: idle                                         |
      | replicas: 1                                          |
      | regions: staging, production                         |
      | Cancel the deployment: No deployment is in progress. |
    And its buttons are
      | Deploy        |
      | Scale the app |

  Scenario: Deploy asks for the environment first, since the region's choices depend on it
    When the player presses "Deploy"
    Then the dialog is titled "Deploy"
    And its body reads
      | Deploys a release of the app to a region of an environment. |
      | Step 1 of 2                                                  |
    And its inputs are
      | input       | kind          |
      | environment | single option |
    And the choices for "environment" are
      | choice     | shown as   |
      | staging    | staging    |
      | production | production |
    And its buttons are
      | Next |
      | Back |

  Scenario: The second step offers only the choices on offer for that environment
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    Then the dialog is titled "Deploy"
    And its body reads
      | Deploys a release of the app to a region of an environment. |
      | Step 2 of 2: environment staging                             |
    And its inputs are
      | input   | kind          |
      | region  | single option |
      | release | single option |
    And the choices for "region" are
      | choice  | shown as |
      | us-east | US East  |
    And the choices for "release" are
      | choice | shown as    |
      | 1.0    | Release 1.0 |
      | 1.1    | Release 1.1 |
    And its buttons are
      | Deploy |
      | Back   |

  Scenario: Back returns to the app, with nothing sent
    When the player presses "Deploy"
    And the player presses "Back"
    Then the dialog is titled "web"
    And its buttons are
      | Deploy        |
      | Scale the app |
    And the deploy service received no acts

  Scenario: Deploying sends the act with If-Match, and the next dialog is the app as the answer left it
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    And the player fills in, and presses "Deploy"
      | region  | us-east |
      | release | 1.1     |
    Then the deploy service's acts, each sent with If-Match, were
      | method | path                     | body                                                         |
      | POST   | /apps/web/actions/deploy | {"environment":"staging","region":"us-east","release":"1.1"} |
    And the dialog is titled "web"
    And its body reads
      | id: 1                                        |
      | name: web                                    |
      | status: deploying                            |
      | environment: staging                         |
      | region: us-east                              |
      | release: 1.1                                 |
      | replicas: 1                                  |
      | regions: staging, production                 |
      | Deploy: A deployment is in progress.         |
      | Scale the app: A deployment is in progress. |
    And its buttons are
      | Cancel the deployment |

  Scenario: Cancelling, with a reason, brings the app back to idle
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    And the player fills in, and presses "Deploy"
      | region  | us-east |
      | release | 1.1     |
    And the player presses "Cancel the deployment"
    Then the dialog is titled "Cancel the deployment"
    And its inputs are
      | input  | kind |
      | reason | text |
    When the player fills in, and presses "Cancel the deployment"
      | reason | wrong release |
    Then the dialog is titled "web"
    And its buttons are
      | Deploy        |
      | Scale the app |
    And the deploy service's acts, each sent with If-Match, were
      | method | path                                            | body                                                         |
      | POST   | /apps/web/actions/deploy                        | {"environment":"staging","region":"us-east","release":"1.1"} |
      | DELETE | /apps/web/actions/cancel?reason=wrong%20release |                                                              |

  Scenario: A number is typed, and sent as a number
    When the player presses "Scale the app"
    Then its inputs are
      | input    | kind |
      | replicas | text |
    When the player fills in, and presses "Scale the app"
      | replicas | 3 |
    Then the deploy service's acts, each sent with If-Match, were
      | method | path                    | body           |
      | PUT    | /apps/web/actions/scale | {"replicas":3} |
    And the dialog is titled "web"

  Scenario: Someone deployed first: the act is refused in the server's words, and not resent
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    And someone else starts deploying "web"
    And the player fills in, and presses "Deploy"
      | region  | us-east |
      | release | 1.1     |
    Then the dialog is titled "Deploy was refused"
    And its body reads
      | cd:deploy was refused (412): the validator does not match the current one of the selected representation; re-read, not resent |
    And its buttons are
      | OK |
    And the deploy service's acts, each sent with If-Match, were
      | method | path                     | body                                                         |
      | POST   | /apps/web/actions/deploy | {"environment":"staging","region":"us-east","release":"1.1"} |
    When the player presses "OK"
    Then the dialog is titled "web"
    And its buttons are
      | Cancel the deployment |
