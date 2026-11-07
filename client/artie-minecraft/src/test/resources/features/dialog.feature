Feature: A HEART resource in Minecraft, as a book and dialogs
  Minecraft has written books, whose text a player can click, and dialogs,
  with buttons and inputs, as a browser has links and forms. ARTIE reads the
  resource, and Minecraft opens it as a book: its title, a link for every act,
  as the web view has a button for every act, and its state as titled values.
  An act on offer says on hover what it does; an absent act's link is grey and
  says why, and clicking it only reads the resource again. Each act's form is
  a dialog of inputs. A dialog cannot change while it is open, so a property
  whose choices depend on another field is asked for after that field. The
  choices are only the ones on offer. An act is sent with If-Match, and its
  answer is the next book. An act refused because the resource changed is
  said in plain words, the refusal itself on hover; any other refusal is shown
  in the server's words. A book's page holds fourteen lines, so a long state
  goes on over the next page. Here a player opens the deploy service's app
  "web".

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

  Scenario: The app, idle: a book of its acts, the absent one greyed with why, and its state
    Then Minecraft opens it as a book titled "web"
    And its buttons, offered or greyed, are
      | button                | shown   | on hover                                                     |
      | Deploy                | offered | Deploys a release of the app to a region of an environment. |
      | Scale the app         | offered | Sets how many instances the app runs on.                     |
      | Cancel the deployment | greyed  | No deployment is in progress.                                |
    And the state shown is
      | Id       | 1                   |
      | Name     | web                 |
      | Status   | idle                |
      | Replicas | 1                   |
      | Regions  | staging, production |

  Scenario: The book's page: the title, a link for each act, the absent one grey, then the state
    Then the book's first page reads
      | web                          |
      |                              |
      | Deploy                       |
      | Scale the app                |
      | Cancel the deployment        |
      |                              |
      | Id: 1                        |
      | Name: web                    |
      | Status: idle                 |
      | Replicas: 1                  |
      | Regions: staging, production |
    And Minecraft draws the book's links as
      | link                  | colour    | underlined |
      | Deploy                | dark blue | yes        |
      | Scale the app         | dark blue | yes        |
      | Cancel the deployment | grey      | no         |
    And Minecraft draws the book's line "Replicas" as
      | text      | colour    |
      | Replicas: | dark grey |
      | 1         | black     |

  Scenario: A greyed act's link sends nothing: it reads the app again, which may offer the act by then
    When someone else starts deploying "web"
    And the player presses "Cancel the deployment"
    Then its buttons, offered or greyed, are
      | button                | shown   | on hover                          |
      | Cancel the deployment | offered | Cancels the deployment under way. |
      | Deploy                | greyed  | A deployment is in progress.      |
      | Scale the app         | greyed  | A deployment is in progress.      |
    And the deploy service received no acts

  Scenario: Deploy asks for the environment first, since the region's choices depend on it
    When the player presses "Deploy"
    Then Minecraft opens a dialog titled "Deploy"
    And its body reads
      | Deploys a release of the app to a region of an environment. |
      | Step 1 of 2                                                  |
    And its inputs are
      | input       | label       | kind          |
      | environment | Environment | single option |
    And the choices for "environment" are
      | choice     | shown as   |
      | staging    | staging    |
      | production | production |
    And its buttons are
      | Next |
      | Back |

  Scenario: The second step offers only the choices on offer for that environment, under what was chosen so far
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    Then Minecraft opens a dialog titled "Deploy"
    And its body reads
      | Deploys a release of the app to a region of an environment. |
      | Step 2 of 2                                                  |
    And it shows
      | Environment | staging |
    And its inputs are
      | input   | label   | kind          |
      | region  | Region  | single option |
      | release | Release | single option |
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
    Then Minecraft opens it as a book titled "web"
    And its buttons are
      | Deploy                |
      | Scale the app         |
      | Cancel the deployment |
    And the deploy service received no acts

  Scenario: Deploying sends the act with If-Match, and the next book is the app as the answer left it, over two pages
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    And the player fills in, and presses "Deploy"
      | region  | us-east |
      | release | 1.1     |
    Then the deploy service's acts, each sent with If-Match, were
      | method | path                     | body                                                         |
      | POST   | /apps/web/actions/deploy | {"environment":"staging","region":"us-east","release":"1.1"} |
    And Minecraft opens it as a book titled "web"
    And the state shown is
      | Id          | 1                   |
      | Name        | web                 |
      | Status      | deploying           |
      | Environment | staging             |
      | Region      | us-east             |
      | Release     | 1.1                 |
      | Replicas    | 1                   |
      | Regions     | staging, production |
    And its buttons, offered or greyed, are
      | button                | shown   | on hover                          |
      | Cancel the deployment | offered | Cancels the deployment under way. |
      | Deploy                | greyed  | A deployment is in progress.      |
      | Scale the app         | greyed  | A deployment is in progress.      |
    And the book's second page reads
      | Replicas: 1                  |
      | Regions: staging, production |

  Scenario: Cancelling, with a reason, brings the app back to idle
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    And the player fills in, and presses "Deploy"
      | region  | us-east |
      | release | 1.1     |
    And the player presses "Cancel the deployment"
    Then Minecraft opens a dialog titled "Cancel the deployment"
    And its inputs are
      | input  | label  | kind |
      | reason | Reason | text |
    When the player fills in, and presses "Cancel the deployment"
      | reason | wrong release |
    Then Minecraft opens it as a book titled "web"
    And its buttons are
      | Deploy                |
      | Scale the app         |
      | Cancel the deployment |
    And the deploy service's acts, each sent with If-Match, were
      | method | path                                            | body                                                         |
      | POST   | /apps/web/actions/deploy                        | {"environment":"staging","region":"us-east","release":"1.1"} |
      | DELETE | /apps/web/actions/cancel?reason=wrong%20release |                                                              |

  Scenario: A number is typed, and sent as a number
    When the player presses "Scale the app"
    Then its inputs are
      | input    | label    | kind |
      | replicas | Replicas | text |
    When the player fills in, and presses "Scale the app"
      | replicas | 3 |
    Then the deploy service's acts, each sent with If-Match, were
      | method | path                    | body           |
      | PUT    | /apps/web/actions/scale | {"replicas":3} |
    And Minecraft opens it as a book titled "web"

  Scenario: Someone deployed first: the app changed since the player looked, so the act was not done, and is not resent
    When the player presses "Deploy"
    And the player fills in, and presses "Next"
      | environment | staging |
    And someone else starts deploying "web"
    And the player fills in, and presses "Deploy"
      | region  | us-east |
      | release | 1.1     |
    Then Minecraft opens it as a book titled "web changed since you looked"
    And its body reads
      | Deploy was not done: web changed after this dialog opened. |
    And its buttons, offered or greyed, are
      | button     | shown   | on hover                                                                                                                       |
      | See it now | offered | cd:deploy was refused (412): the validator does not match the current one of the selected representation; re-read, not resent |
    And the deploy service's acts, each sent with If-Match, were
      | method | path                     | body                                                         |
      | POST   | /apps/web/actions/deploy | {"environment":"staging","region":"us-east","release":"1.1"} |
    When the player presses "See it now"
    Then Minecraft opens it as a book titled "web"
    And its buttons, offered or greyed, are
      | button                | shown   | on hover                          |
      | Cancel the deployment | offered | Cancels the deployment under way. |
      | Deploy                | greyed  | A deployment is in progress.      |
      | Scale the app         | greyed  | A deployment is in progress.      |
