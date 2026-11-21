# Studs and Tubes, at the Kitchen Table

*A fictional conversation. Saturday morning. Josh, his son Sam (8), and Josh's coworker Doca, who stopped by to
borrow a ladder and stayed for coffee. A bin of Lego is tipped out on the table.*

---

**Sam:** Dad, look. I put the wheel thing from the space set on the pirate ship.

**Josh:** Does it work?

**Sam:** It's a pirate ship that drives now.

**Josh:** Of course. Here's a question for you, though. The people who made the space set and the people who made
the pirate ship, did they talk to each other?

**Sam:** I don't know. Probably not?

**Josh:** Probably not. Different sets, different years. So how did the wheel know how to fit the ship?

**Sam:** It didn't *know*. It's got the bumps.

**Josh:** The bumps. Lego calls them studs. Flip a brick over. What's underneath?

**Sam:** Circles. The hollow ones.

**Josh:** Tubes. Studs on top, tubes underneath. Every brick has the same ones, the same size, the same distance
apart. So any brick fits any other brick, even ones nobody has thought up yet.

**Sam:** Even old ones?

**Josh:** Even old ones. Trivia aside: Lego got a patent for the stud and tube in 1958. A brick from back then still
snaps onto one made last week. Grandpa's bricks fit yours.

**Sam:** Whoa.

**Doca:** And when it snaps, it holds. Pull them apart. Feel that?

**Sam:** It's kind of hard.

**Doca:** Lego has a name for that. Clutch power. Tight enough to hold, loose enough to take apart. Remember that
one. Your dad's going to steal it in a minute.

**Josh:** I am. Now, Sam, who decided the wheel goes on the ship?

**Sam:** Me.

**Josh:** Right. The bricks don't decide anything. They just all speak the same bump-and-hole language. *You're* the
one who looks at the pieces, sees what fits where, and snaps them together. You're the builder.

**Sam:** I'm the builder.

---

**Josh:** Okay, Doca, this is where it gets good. Computers on the Internet have the same problem as the space set
and the pirate ship. Millions of programs, written by people who never met. How do they fit together?

**Doca:** Mostly with a lot of glue.

**Josh:** Mostly with a lot of glue. Except for one place where it works the way Lego works. Sam, when you go to a
new website on the iPad, does the iPad need a special app for that one site?

**Sam:** No. You just tap stuff.

**Josh:** You just tap stuff. The browser has never seen that site before, and it still shows you the words and the
pictures, and the buttons you can press, and the boxes you can type in. Every site, same browser.

**Sam:** So the browser has studs.

**Josh:** The browser and every website share the same studs and tubes. A man named Roy Fielding studied why the Web
worked so well and wrote it down in 2000. He called the style REST. The middle of it, the most important part, he
called the *uniform interface*. Uniform means "the same everywhere." It's the stud and tube.

**Doca:** Fielding didn't invent a new thing, Sam. He looked at the Web, which already worked, and explained why.

**Sam:** Like a scientist looking at bugs.

**Josh:** Exactly like that.

---

**Josh:** Now, every web page a person sees comes with two things mixed together. Sam, what's on the screen when you
look up a Lego set on the website?

**Sam:** The picture. How many pieces. The price.

**Josh:** That's the *information*. What else?

**Sam:** The "add to bag" button. And the arrow to the next one.

**Josh:** Those are the *controls*. The things you can do next. Fielding says hypertext is exactly that: information
and controls together, so the reader can choose what to do.

**Doca:** And here's where the grown-ups messed up.

**Josh:** Here's where we messed up. When programs talk to other programs, which we call an API, we kept the
information and threw away the controls. The program gets the picture and the price, and no button. No arrow.

**Sam:** Then how does it know what to do?

**Josh:** Somebody writes the instructions down in a big document, and a programmer reads it, and writes code ahead
of time that says "to buy a thing, go to *this* exact address and send *these* exact words." For every site.
Separately.

**Sam:** That's like gluing the wheel on the ship.

**Doca:** That's exactly what it's like. Superglue. Works great until somebody changes the ship.

**Sam:** And then you can't get it off.

**Josh:** And then you can't get it off. I call it the divorce. The information and the controls got divorced.

---

**Josh:** So, Sam, that's what Doca and I work on. It's called HEART. It's a way for programs to talk where the
information and the controls come back together. Every answer a program gets says what the thing *is*, and also what
you can *do* with it right now.

**Sam:** Like the buttons came back.

**Josh:** The buttons came back. In HEART there are two kinds. A *link* says "you can go there." A *form* says "you
can do this, here's where to send it, and here's what to fill in."

**Doca:** Tell him about the merge button.

**Josh:** At work we have a thing called a pull request. It's somebody saying "here's some new code, can I add it?"
When it's ready, HEART's answer for it includes a form called `scm:merge`. Merge means "put it in."

**Sam:** And if it's not ready?

**Josh:** Then the form isn't there. The program can't press a button that isn't on the screen.

**Sam:** That's smart.

**Josh:** It's how your iPad already works. A grayed-out button is the website telling you "not now." HEART does it
for programs. It can even say *why* not now: there's a link for that, `hr:unavailable`, that lists the reasons.

**Doca:** Now steal clutch power.

**Josh:** Thank you. When a program sends a form, it says "I'm doing this to the version I just looked at." If
somebody else changed the thing in the meantime, the server says no. That's error 412. Nothing half-done. Either it
snaps on, or it doesn't snap at all.

**Sam:** Clutch power.

**Josh:** Clutch power.

---

**Doca:** Sam, there's one more brick in the bin. Who builds with HEART?

**Sam:** Me?

**Josh:** A program called ARTIE. ARTIE is the builder. It doesn't know about pull requests, or libraries, or Lego
stores ahead of time. It knows the studs and tubes: how to read an answer, find the links and the forms, fill a form
in and send it. Like you know how bricks snap, without knowing what any set is supposed to be.

**Sam:** But how does ARTIE know what to build? I know because I look at the box.

**Josh:** Great question. Somebody tells ARTIE the goal in plain words, like "merge pull request 501 by squashing."
The first time, ARTIE asks an AI to read the words and pick which form fits. That costs a little money each time the
AI thinks.

**Doca:** And then?

**Josh:** And then ARTIE writes down every step it took, like the instruction booklet. Next time, it follows the
booklet. No AI, no money, same steps.

**Sam:** You made the instructions by building it once.

**Josh:** We made the instructions by building it once. And here's the safety part. Before every step, ARTIE checks
the form still looks like the one in the booklet. If the server changed it, ARTIE stops instead of guessing.

**Sam:** Like if the piece in the bag is different from the picture.

**Josh:** Exactly that. You don't force it. You go ask.

---

**Doca:** My turn. Sam, have you ever built something and then had to wait for a part?

**Sam:** Yeah. Mom ordered the one gray piece I lost.

**Doca:** Did you stand by the door all day?

**Sam:** No! I played. Then it came in the mail.

**Doca:** That's RISE. Some jobs take a long time. Building a program can take minutes. A person reviewing your code
can take a day. Without RISE, a program keeps asking "is it done? is it done? is it done?"

**Sam:** That's annoying.

**Doca:** Very annoying. With RISE, the program says "tell me when it's done, and here's where to send the answer."
It writes its return address on every letter it sends. The other side doesn't have to remember anybody's address. It
just looks at the letter.

**Sam:** Like the corner of an envelope.

**Doca:** Exactly like the corner of an envelope. Your dad already built one way to do it, using the Web. The return
address is your house. When the answer's ready, the other side walks it right up to your door.

**Josh:** And the answer is signed, so ARTIE knows it really came from who it says.

**Doca:** But what if you live behind a gate?

**Sam:** Like Grandma's neighborhood? With the guard?

**Doca:** Like Grandma's. The mail carrier can't just walk in. Lots of programs live behind a gate like that,
especially the ones on somebody's laptop. So with the Web way, you need a guard at your gate who takes the letter,
checks who it's from, and carries it to your door. And sometimes a guard at the sender's gate too, who makes sure the
letter goes out and tries again if nobody answers. Grown-ups have a program for that guard. It's called Envoy.

**Sam:** That's a lot of guards.

**Doca:** It is. So I'm working on another way. Do you know what a post office box is?

**Sam:** No.

**Doca:** It's a little locked box at the post office, with your number on it. Your mail doesn't come to your house.
It goes to the post office and waits in your box. Whenever you're ready, you go and get it.

**Sam:** So nobody has to get through the gate.

**Doca:** Nobody has to get through the gate. You go out to the post office, and so does everybody else. That's easy,
because going out is always allowed. Grown-ups call the post office a broker, and the line of letters waiting in your
box a queue.

**Sam:** Why a line?

**Doca:** Because if lots of answers come while you're asleep, they wait in order. First one in, first one out.
Nothing gets lost.

**Josh:** Now tell him the good part. What's inside the letter.

**Doca:** Okay, Sam. The best part is what *doesn't* change. Remember the Lego set page, with the picture, the price
and the "add to bag" button?

**Sam:** Information and controls.

**Doca:** You were listening! When that page comes by post office box, the inside of the letter is exactly the same.
Same picture, same price, same buttons. Not almost the same. The same, letter for letter. Nothing inside even says it
came by post office.

**Sam:** So the bricks are the same, just in a different box.

**Doca:** Same bricks, different delivery truck. Only the outside of the envelope changes. Want to know what's written
on the outside?

**Sam:** Yeah.

**Doca:** Four things. First, who it's to. Not "the post office." The exact thing you mean, like "pull request five
oh one." Second, what you want, written small in the corner: "show me," or "merge it." Third, your return address,
your box number. And fourth, a ticket number, so when the answer comes back you know which question it answers.

**Sam:** Like at the deli! Number forty-two!

**Doca:** Exactly like the deli. And the answer comes back with a number on the outside too. Two hundred means
"done." Four twelve means "somebody changed it before you got there, look again."

**Josh:** The same four twelve as on the Web.

**Doca:** The same four twelve. Same meanings, same clutch power. And when ARTIE says "I'm doing this to the version I
just looked at," that goes on the outside too, so the post office doesn't have to open your letter to deliver it.

**Josh:** Here's the part I like best. Sam, the Lego store takes orders three ways. You can walk up to the counter.
You can call on the phone. Or you can mail in a card. Three doors. Who packs your order?

**Sam:** The person in the back?

**Josh:** The same person in the back, every time. They look up the set, check it's still in stock, check you asked
for one they actually sell, and pack it. The door only changes how the order gets in and how the answer gets out.

**Doca:** The Web is the counter. The post office box is the mail-in card. Same person in the back. Same rules.

**Sam:** What if the mail gets lost?

**Doca:** The post office is careful. It keeps the letter until the person in the back says "got it, and I'm done."
If they trip and drop it, the post office hands it to them again.

**Sam:** Then they'd do it twice!

**Doca:** Good catch. That's why ARTIE writes the version on the outside. The second time, the person in the back
sees it was already done to that version, and says four twelve. Nothing happens twice.

**Sam:** How does ARTIE know whether to use the house or the box?

**Doca:** It reads the address. A Web address starts with h-t-t-p-s. A post office address starts with a-m-q-p-s. One
answer can even have both. "Watch it over here, by post office box. Merge it over there, on the Web." ARTIE picks the
truck from each address, one at a time.

**Sam:** Can you mix them on one letter?

**Doca:** You can. Little updates, like "half done," can go in the box, and the big final answer can come to the
house. And there's one rule: if a letter came sealed, you never answer it on a postcard. The S at the end of
a-m-q-p-s and h-t-t-p-s means sealed.

**Josh:** And the post office checks ID?

**Doca:** When you open your box, yes. And you can write "only worth answering until Tuesday" on the outside. After
Tuesday, the post office throws it away, so nobody acts on old news.

**Sam:** Can you get mail you didn't ask for? Like a magazine?

**Doca:** That's my favorite part. You can subscribe. "Every time pull request five oh one changes, put a note in my
box." Then ARTIE doesn't even ask "is it done?" It just checks its box.

**Josh:** Same letter, same studs, same tubes.

**Doca:** Same letter. The only difference is where it waits. Your dad's way, it comes to your door. My way, it waits
in your box.

**Sam:** So RISE is the mail.

**Doca:** RISE is the mail. HEART is the bricks. And the post office is just one more way to deliver them.

---

**Sam:** Dad, can ARTIE build with *any* program?

**Josh:** With any program that speaks HEART. And for the ones that don't, like GitHub, we put an adapter in front.
You know the Duplo bricks, the big ones?

**Sam:** The baby ones.

**Josh:** The baby ones. They still fit on regular Lego, because somebody made them on purpose to match. Our adapter
does that for GitHub. GitHub on one side, HEART studs on the other. We did the same for GitLab, which is a different
company with different words for everything, and ARTIE didn't have to change at all.

**Doca:** Same plug, different wall.

**Sam:** So the wheel fits the pirate ship *and* the space ship.

**Josh:** The wheel fits everything. That's the whole idea.

**Sam:** Can I have more bricks?

**Josh:** No. But you can have the ladder bin. Doca's taking the ladder.

**Doca:** I did come for the ladder.

---

## Notes for the author

- Facts checked from memory, not looked up today: the stud-and-tube patent is 1958; Lego uses the term "clutch
  power"; Duplo is made to connect with standard bricks. Worth a quick look before this is read aloud anywhere.
- Fielding's information-and-controls passage is paraphrased here, not quoted. If a slide quotes it, quote it whole
  from his source.
- "Doca built RISE" stays inside the fiction, as in "Rest, then RISE." In the repository, RISE replies over HTTP exist
  in heart-core (Replies.kt) and the release runs on them. The post office box, RISE over a broker with AMQP 1.0, is a
  plan only: spec/ideas/amqp-binding.md, amqp-binding-vision.md and rise-awakening.md.
- What the post office part stands for:
  - the envelope's outside: the resource's URI in `to`, the method in `subject`, `reply-to`, and `message-id` with the
    answer's `correlation-id`; the ETag in application properties; the status number in the answer's `subject`;
  - "only worth answering until Tuesday": `absolute-expiry-time`;
  - "same person in the back": one core behind every front door (HTTP, CoAP, and the planned heart-amqp);
  - "got it, and I'm done": the front door settles the message only after the outcome is decided; a redelivery is
    caught by the validator (412);
  - "never answer a sealed letter on a postcard": no answer over a weaker scheme than the request came on;
  - the subscription: change notices, an idea in amqp-binding.md.
- Envoy as the gate guard: a proxy taking callbacks in for a client behind a firewall, and on the sender's side
  sending them out with retries. The author's own framing, to confirm.
- Sam is your son, written as he/him. Doca is he/him.
- For speech synthesis, write Doca as "Docka."
