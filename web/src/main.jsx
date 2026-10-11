import React, { useState } from "react";
import { createRoot } from "react-dom/client";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useMutation,
} from "@tanstack/react-query";
import "./style.css";
function App() {
  const [token, setToken] = useState(""),
    [approver, setApprover] = useState(""),
    [service, setService] = useState("vpn"),
    [description, setDescription] = useState(
      "VPN is unavailable for the support team.",
    ),
    [id, setId] = useState("");
  async function api(path, body, credential = token) {
    const r = await fetch("/api/" + path, {
      method: body ? "POST" : "GET",
      headers: {
        "Content-Type": "application/json",
        Authorization: "Bearer " + credential,
      },
      body: body ? JSON.stringify(body) : undefined,
    });
    const data = await r.json();
    if (!r.ok) throw Error(data.error);
    return data;
  }
  const run = useMutation({
    mutationFn: () => api("incidents", { service, description }),
    onSuccess: (d) => setId(d.id),
  });
  const query = useQuery({
    queryKey: ["incident", id, token],
    queryFn: () => api("incidents/" + id),
    enabled: !!id,
    refetchInterval: 2000,
    retry: false,
  });
  const decision = useMutation({
    mutationFn: (approved) =>
      api("incidents/" + id + "/decision", { approved }, approver),
    onSuccess: () => query.refetch(),
  });
  const data = query.data;
  return (
    <>
      <header>
        <b>◈ OPERATIONS / AGENT</b>
        <span>Controlled execution workspace</span>
      </header>
      <main>
        <div className="eyebrow">Enterprise IT operations</div>
        <h1>Investigate. Review. Resolve.</h1>
        <p className="muted">
          Operational context, durable workflows, and an explicit human decision
          before every action.
        </p>
        <div className="grid">
          <section className="card">
            <h2>New investigation</h2>
            <label>
              Operator token
              <input
                type="password"
                value={token}
                onChange={(e) => setToken(e.target.value)}
              />
            </label>
            <label>
              Service
              <select
                value={service}
                onChange={(e) => setService(e.target.value)}
              >
                <option>vpn</option>
                <option>email</option>
                <option>helpdesk</option>
              </select>
            </label>
            <label>
              Request
              <textarea
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
            <button
              disabled={!token || run.isPending}
              onClick={() => run.mutate()}
            >
              Start investigation →
            </button>
            <p className="note">
              The bundled IT adapter simulates remediation. Live Portkey
              planning is enabled only when gateway credentials are configured.
            </p>
          </section>
          <section className="card">
            <h2>Execution timeline</h2>
            <span className="badge">
              {data?.status || "Ready for a request"}
            </span>
            {id && <p className="muted">{id}</p>}
            {data?.plan && (
              <>
                <h2>{data.plan.action.replaceAll("_", " ")}</h2>
                <p>{data.plan.reason}</p>
                <p className="muted">Evidence: {data.plan.evidence}</p>
                <p className="muted">
                  {data.plan.tokens} tokens · {data.plan.latencyMs} ms gateway
                  latency
                </p>
              </>
            )}
            {data?.status === "awaiting_approval" && (
              <div>
                <label>
                  Approver token
                  <input
                    type="password"
                    value={approver}
                    onChange={(e) => setApprover(e.target.value)}
                  />
                </label>
                <button
                  disabled={!approver || decision.isPending}
                  onClick={() => decision.mutate(true)}
                >
                  Approve restart
                </button>
                <button
                  className="secondary"
                  disabled={!approver || decision.isPending}
                  onClick={() => decision.mutate(false)}
                >
                  Reject
                </button>
              </div>
            )}
            <pre>
              {data?.events?.join("\n") || "Activity events will appear here."}
            </pre>
            {[run.error, query.error, decision.error]
              .filter(Boolean)
              .map((e, i) => (
                <p className="error" key={i}>
                  {e.message}
                </p>
              ))}
          </section>
        </div>
      </main>
    </>
  );
}
createRoot(document.getElementById("root")).render(
  <QueryClientProvider client={new QueryClient()}>
    <App />
  </QueryClientProvider>,
);
