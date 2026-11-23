// The walks in this report, as they call heart-cs. The recording around each call is left out; this file
// compiles against HeartCs.Core and HeartCs.Http as shown.
using System.Text.Json.Nodes;
using HeartCs.Core;
using HeartCs.Http;

public static class Walks
{
    // 1. Wiring. The core knows only its own port, ITransport. HTTP is one adapter, handed in.
    public static HeartClient Client(Rung rung, Dialect dialect, string credential)
    {
        ITransport transport = new HttpClientTransport(new HttpClient());
        return new HeartClient(transport, credential, rung: rung, dialect: dialect);
    }

    // 2. The walk. The same code in HAL-FORMS at L0 and HAL Schema Forms at L2: only the client's arguments
    //    differ. Acts are named by relation; the client finds each one's address and method in what it holds.
    public static async Task HireAndPromote(HeartClient client)
    {
        await client.EnterAsync(new Uri("http://employees.test/"));

        await client.ActAsync("staff:hire", new JsonObject
        {
            ["firstName"] = "Samwise", ["lastName"] = "Gamgee", ["role"] = "gardener",
        });
        // The client now holds /employees/3, the resource the 201 named.

        await client.ActAsync("staff:update", new JsonObject
        {
            ["firstName"] = "Samwise", ["lastName"] = "Gamgee", ["role"] = "gardener, mayor",
        });

        await client.ActAsync("staff:dismiss", []);
        Console.WriteLine(client.Current!.State["status"]); // dismissed
    }

    // 3. Moved. The service renamed its acts' addresses (/actions/take-on, /actions/revise). This walk
    //    hires and updates with the same calls as above, unchanged: the new targets arrive in the forms.

    // 4. Someone else acts first. The update carries the ETag the client read; the service answers 412.
    //    The client refuses as "stale", reads Frodo again, and never resends over the other person's change.
    public static async Task RetireFrodo(HeartClient client)
    {
        await client.EnterAsync(new Uri("http://employees.test/"));
        await client.ActAsync("staff:search-employees", new JsonObject { ["name"] = "Frodo" });
        await client.FollowAsync("item", "Frodo Baggins");
        try
        {
            await client.ActAsync("staff:update", new JsonObject
            {
                ["firstName"] = "Frodo", ["lastName"] = "Baggins", ["role"] = "ring bearer, retired",
            });
        }
        catch (RefusedException refused) when (refused.Kind == "stale")
        {
            Console.WriteLine(refused.Status);                   // 412
            Console.WriteLine(client.Current!.State["role"]);    // keeper of the Red Book
        }
    }

    // 5. The port. Anything that can send a request and return an answer can carry the client: HTTP, recorded
    //    exchanges for offline tests, or this test adapter, which routes the .test hosts to the simulator.
    public sealed class SimsTransport(ITransport inner, Uri simsBase) : ITransport
    {
        public Task<HeartResponse> SendAsync(HeartRequest request)
        {
            var headers = new Dictionary<string, string>(request.Headers) { ["X-Sim-Host"] = request.Uri.Authority };
            return inner.SendAsync(request with { Uri = new Uri(simsBase, request.Uri.PathAndQuery), Headers = headers });
        }
    }
}
