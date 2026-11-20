#!/usr/bin/perl
# library.cgi, as it actually happened -- Riverside Public Library's loans in 1997, in the web we got: HTML 2.0 had
# forms (RFC 1866, November 1995), so the page carries its own controls. The same loans, rules and reasons as the
# web that wasn't (exhibits/the-web-that-wasnt/perl/cgi-bin/library.cgi); two things differ. Her loans page writes,
# on each loan, a form for each act it allows now, and the reason in place of the one it doesn't. And she signs in
# with a form, so her card and PIN travel in a POST body, never in the address.
#   REQUEST_METHOD=GET PATH_INFO=/loans perl library.cgi          (the sign-in form)
use strict;
use warnings;
use CGI qw(:standard -no_xhtml);
use Fcntl qw(:flock);
use Time::Local qw(timegm);

my $DATA = $ENV{LIBRARY_DATA} || '/data/loans.txt';
my $SCRIPT = '/cgi-bin/library.cgi';
my %MEMBER = (card => '21000451', pin => '1234');    # Ana Torres
my @SEED = (
    # loan  book  title              author              due          renewals hold returned
    [3310, 4471, 'The Hobbit',      'J. R. R. Tolkien', '1997-11-12', 0, 1, 0],
    [3311, 4472, 'Matilda',         'Roald Dahl',       '1997-11-12', 0, 0, 0],
    [3312, 4473, "Charlotte's Web", 'E. B. White',      '1997-11-12', 3, 0, 0],
);
my $MOST_RENEWALS = 3;

my $q = CGI->new;
my $path = $q->path_info || '/';
my $method = $q->request_method || 'GET';

open(my $lock, '>>', "$DATA.lock") or die "cannot lock: $!";
flock($lock, LOCK_EX);
my @loans = load();

if ($method eq 'POST' && $path eq '/_reset') { @loans = map { [@$_] } @SEED; save(); answer('200 OK', 'Reset', p('The seed is back.')); }
elsif ($method eq 'GET') { sign_in(); }
elsif (($q->param('card') // '') ne $MEMBER{card} || ($q->param('pin') // '') ne $MEMBER{pin}) {
    answer('401 Unauthorized', 'Not signed in', p('Wrong card number or PIN.'), sign_in_form());
}
elsif ($path eq '/loans') { list(); }
elsif ($path =~ m{^/loans/(\d+)/(renew|return)$}) { act($1, $2); }
else { answer('404 Not Found', 'Not found', p("Nothing is at $path.")); }
close $lock;
exit;

# Signing in is a form too: the card and PIN go in the body of a POST, where nobody behind her can read them.
sub sign_in { answer('200 OK', 'Sign in', sign_in_form()); }

sub sign_in_form {
    return start_form(-method => 'POST', -action => "$SCRIPT/loans"),
        p('Library card number: ', textfield(-name => 'card', -size => 10)),
        p('PIN: ', password_field(-name => 'pin', -size => 4)),
        submit(-value => 'Show my loans'), end_form();
}

# Her loans, each with what it allows now: a button for each act it offers, and the reason for the one it doesn't.
sub list {
    my ($said) = @_;
    my @rows = (Tr(th(['Loan', 'Title', 'Due', ''])));
    for my $loan (grep { !$_->[7] } @loans) {
        my $id = $loan->[0];
        my $renew = $loan->[6] ? i("A hold is waiting for this book, so it can't be renewed.")
            : $loan->[5] >= $MOST_RENEWALS ? i("It has been renewed $MOST_RENEWALS times, the most allowed.")
            : form_for($id, 'renew', 'Renew');
        push @rows, Tr(td([$id, escapeHTML($loan->[2]), $loan->[4], $renew . ' ' . form_for($id, 'return', 'Return')]));
    }
    answer('200 OK', 'Your loans', ($said ? p(b($said)) : ()), table({ -border => 1, -cellpadding => 4 }, @rows));
}

# One act on one loan, as a form with one button. The card and PIN ride along, hidden, in the body it sends.
sub form_for {
    my ($id, $what, $label) = @_;
    return start_form(-method => 'POST', -action => "$SCRIPT/loans/$id/$what")
        . hidden(-name => 'card', -default => scalar $q->param('card'), -override => 1)
        . hidden(-name => 'pin', -default => scalar $q->param('pin'), -override => 1)
        . submit(-value => $label) . end_form();
}

# The same rules as the web that wasn't, checked again when the act arrives: the page offered it, the server decides.
sub act {
    my ($id, $what) = @_;
    my $loan = find($id) or return answer('404 Not Found', 'No such loan', p("There is no loan $id."));
    return answer('409 Conflict', 'Cannot do that', p('It was returned already.')) if $loan->[7];
    if ($what eq 'return') {
        $loan->[7] = 1;
        save();
        return list("Loan $id is returned. Thank you.");
    }
    return answer('409 Conflict', 'Cannot renew', p("A hold is waiting for this book, so it can't be renewed."))
        if $loan->[6];
    return answer('409 Conflict', 'Cannot renew', p("It has been renewed $MOST_RENEWALS times, the most allowed."))
        if $loan->[5] >= $MOST_RENEWALS;
    my ($y, $m, $d) = split /-/, $loan->[4];
    my @due = gmtime(timegm(0, 0, 12, $d, $m - 1, $y) + 21 * 86400);
    $loan->[4] = sprintf('%04d-%02d-%02d', $due[5] + 1900, $due[4] + 1, $due[3]);
    $loan->[5]++;
    save();
    list("Loan $id is renewed. It is due $loan->[4].");
}

sub find { my ($id) = @_; my ($loan) = grep { $_->[0] == $id } @loans; return $loan; }

sub answer {
    my ($status, $title, @body) = @_;
    print header(-status => $status, -type => 'text/html', -charset => 'ISO-8859-1');
    print start_html(-title => "Riverside Public Library -- $title", -bgcolor => '#FFFFFF'),
          h2($title), @body, hr(), p(small("Riverside Public Library. $status.")), end_html();
    return;
}

sub load {
    return map { [@$_] } @SEED unless -e $DATA;
    open(my $in, '<', $DATA) or die "cannot read $DATA: $!";
    my @read = map { chomp; [split /\t/] } <$in>;
    close $in;
    return @read;
}

sub save {
    open(my $out, '>', $DATA) or die "cannot write $DATA: $!";
    print {$out} join("\t", @$_), "\n" for @loans;
    close $out;
}
