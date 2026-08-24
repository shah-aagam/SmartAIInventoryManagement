import { useEffect, useMemo, useState } from "react";
import "./App.css";

const API = import.meta.env.VITE_API_URL || "http://localhost:8080";

const demoProducts = [
  ["PRD-001", "Wireless Earbuds Pro", "ELECTRONICS", 79.99, 45, 20, 3],
  ["PRD-002", "USB-C Hub 7-Port", "ELECTRONICS", 34.99, 120, 30, 1],
  ["PRD-003", "Organic Cotton T-Shirt", "APPAREL", 24.99, 16, 15, 12],
  ["PRD-004", "Running Shorts Navy", "APPAREL", 39.99, 55, 20, 2],
  ["PRD-005", "Ceramic Pour-Over Set", "HOME", 49.99, 22, 10, 4],
  ["PRD-006", "LED Desk Lamp", "HOME", 59.99, 0, 15, 0],
  ["PRD-007", "Portable Charger 20K", "ELECTRONICS", 44.99, 18, 25, 8],
  ["PRD-008", "Hoodie Heather Grey", "APPAREL", 54.99, 11, 12, 15],
].map(([sku, name, category, currentPrice, stockLevel, reorderThreshold, demandVelocity]) => ({
  id: sku,
  sku,
  name,
  category,
  currentPrice,
  stockLevel,
  reorderThreshold,
  demandVelocity,
  status: stockLevel === 0 ? "OUT_OF_STOCK" : "ACTIVE",
}));

const money = (value) =>
  new Intl.NumberFormat("en-US", {
    style: "currency",
    currency: "USD",
  }).format(Number(value || 0));

const title = (value = "") => String(value).replaceAll("_", " ").toLowerCase();

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });

  if (!response.ok) {
    throw new Error(`Request failed: ${response.status}`);
  }

  if (response.status === 204) {
    return null;
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

const asArray = (payload) => {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.content)) return payload.content;
  if (Array.isArray(payload?.items)) return payload.items;
  if (Array.isArray(payload?.data)) return payload.data;
  return [];
};

const normalizeProduct = (product) => ({
  id: product.id || product.sku,
  sku: product.sku || product.id || "-",
  name: product.name || "Unnamed product",
  category: product.category || "UNCATEGORIZED",
  currentPrice: Number(product.currentPrice ?? product.price ?? 0),
  stockLevel: Number(product.stockLevel ?? product.stock ?? 0),
  reorderThreshold: Number(product.reorderThreshold ?? product.threshold ?? 0),
  demandVelocity: Number(product.demandVelocity ?? product.velocity ?? 0),
  status: product.status || "ACTIVE",
});

const normalizeSuggestion = (suggestion, type) => ({
  ...suggestion,
  type,
  id: suggestion.id,
  status: suggestion.status || "PENDING",
  triggerReason: suggestion.triggerReason || "MANUAL",
  product: normalizeProduct(suggestion.product || {}),
  currentPrice: Number(suggestion.currentPrice ?? suggestion.product?.currentPrice ?? 0),
  recommendedPrice: Number(suggestion.recommendedPrice ?? 0),
  currentStock: Number(suggestion.currentStock ?? suggestion.product?.stockLevel ?? 0),
  recommendedQuantity: Number(suggestion.recommendedQuantity ?? 0),
  suggestedLeadTimeDays: Number(suggestion.suggestedLeadTimeDays ?? 0),
  confidence: Number(suggestion.confidence ?? 0),
  reasoning: suggestion.reasoning || "Generated recommendation is ready for review.",
});

export default function App() {
  const [products, setProducts] = useState(demoProducts);
  const [pricingSuggestions, setPricingSuggestions] = useState([]);
  const [reorderSuggestions, setReorderSuggestions] = useState([]);
  const [strategy, setStrategy] = useState("rule");
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState("");
  const [notice, setNotice] = useState("Connecting to Spring Boot on port 8080...");

  const load = async ({ quiet = false } = {}) => {
    if (!quiet) setLoading(true);

    try {
      const [productsPayload, pricingPayload, reorderPayload, strategyPayload] =
        await Promise.all([
          request("/products"),
          request("/pricing-suggestions"),
          request("/reorder-suggestions"),
          request("/strategy"),
        ]);

      setProducts(asArray(productsPayload).map(normalizeProduct));
      setPricingSuggestions(
        asArray(pricingPayload).map((item) => normalizeSuggestion(item, "price")),
      );
      setReorderSuggestions(
        asArray(reorderPayload).map((item) => normalizeSuggestion(item, "reorder")),
      );
      setStrategy(strategyPayload?.active || "rule");
      setNotice("");
    } catch {
      setProducts((current) => (current.length ? current : demoProducts));
      if (!quiet) {
        setNotice("Backend is not reachable yet. Showing demo catalog until Spring Boot is running on port 8080.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    const initialLoad = setTimeout(() => load(), 0);
    const refresh = setInterval(() => load({ quiet: true }), 4000);
    return () => {
      clearTimeout(initialLoad);
      clearInterval(refresh);
    };
  }, []);

  const pending = useMemo(
    () =>
      [...pricingSuggestions, ...reorderSuggestions].filter(
        (item) => item.status === "PENDING",
      ),
    [pricingSuggestions, reorderSuggestions],
  );

  const lowStock = products.filter(
    (item) => item.stockLevel <= item.reorderThreshold,
  ).length;

  const runAction = async (id, label, action) => {
    setBusy(id);
    try {
      await action();
      setNotice(label);
      await load({ quiet: true });
    } catch {
      setNotice("Action failed. Check that the backend is running and CORS allows http://localhost:5173.");
    } finally {
      setBusy("");
    }
  };

  const recordSale = (product) =>
    runAction(product.id, "Sale recorded. Recommendation engine is processing the signal.", () =>
      request(`/products/${product.id}/orders`, {
        method: "POST",
        body: JSON.stringify({ quantity: 1 }),
      }),
    );

  const requestSuggestion = (product, kind) =>
    runAction(product.id, "Manual recommendation requested.", () =>
      request(`/products/${product.id}/suggest-${kind}`, { method: "POST" }),
    );

  const decide = (suggestion, status) =>
    runAction(suggestion.id, `Suggestion ${status.toLowerCase()}.`, () =>
      request(`/${suggestion.type === "price" ? "pricing" : "reorder"}-suggestions/${suggestion.id}`, {
        method: "PATCH",
        body: JSON.stringify({ status }),
      }),
    );

  const chooseStrategy = (active) =>
    runAction(`strategy-${active}`, "Strategy updated.", async () => {
      const payload = await request("/strategy", {
        method: "PUT",
        body: JSON.stringify({ active }),
      });
      setStrategy(payload?.active || active);
    });

  return (
    <main>
      <header className="app-header">
        <div>
          <label>SHOPSTREAM / MERCHANDISING CONSOLE</label>
          <h1>
            Stock<span>Pulse</span>
          </h1>
          <p>Inventory, pricing, and replenishment decisions in one review queue.</p>
        </div>

        <div className="strategy">
          <label>ACTIVE STRATEGY</label>
          <div className="segmented">
            <button
              className={strategy === "rule" ? "active" : ""}
              disabled={busy === "strategy-rule"}
              onClick={() => chooseStrategy("rule")}
            >
              Rules
            </button>
            <button
              className={strategy === "ai" ? "active" : ""}
              disabled={busy === "strategy-ai"}
              onClick={() => chooseStrategy("ai")}
            >
              AI advisor
            </button>
          </div>
        </div>
      </header>

      <section className="stats" aria-label="Dashboard summary">
        <div>
          <b>{products.length}</b>
          <span>Products monitored</span>
        </div>
        <div>
          <b>{pending.length}</b>
          <span>Pending decisions</span>
        </div>
        <div>
          <b>{lowStock}</b>
          <span>Low stock signals</span>
        </div>
      </section>

      {(notice || loading) && (
        <aside>{loading ? "Loading backend data..." : notice}</aside>
      )}

      <div className="section-title">
        <div>
          <label>REVIEW QUEUE</label>
          <h2>Recommendations awaiting approval</h2>
        </div>
        <button onClick={() => load()} disabled={loading}>
          Refresh
        </button>
      </div>

      <section className="queue">
        {pending.length ? (
          pending.map((item) => (
            <article className="suggestion" key={`${item.type}-${item.id}`}>
              <div className="row">
                <small className={`tag ${item.triggerReason}`}>{title(item.triggerReason)}</small>
                <small>{Math.round(item.confidence * 100)}% confidence</small>
              </div>
              <h3>{item.product.name}</h3>
              <div className="recommendation">
                {item.type === "price" ? (
                  <>
                    <span>{money(item.currentPrice)}</span>
                    <strong>{money(item.recommendedPrice)}</strong>
                  </>
                ) : (
                  <>
                    <span>{item.currentStock} units now</span>
                    <strong>Order {item.recommendedQuantity}</strong>
                  </>
                )}
              </div>
              <p>{item.reasoning}</p>
              {item.type === "reorder" && item.suggestedLeadTimeDays > 0 && (
                <small>Lead time: {item.suggestedLeadTimeDays} days</small>
              )}
              <div className="actions">
                <button
                  className="accept"
                  disabled={busy === item.id}
                  onClick={() => decide(item, "ACCEPTED")}
                >
                  Accept
                </button>
                <button disabled={busy === item.id} onClick={() => decide(item, "REJECTED")}>
                  Reject
                </button>
              </div>
            </article>
          ))
        ) : (
          <article className="empty">
            No pending suggestions. Trigger a sale or request a manual recommendation from the catalog.
          </article>
        )}
      </section>

      <div className="section-title catalog-title">
        <div>
          <label>LIVE CATALOG</label>
          <h2>Inventory signals</h2>
        </div>
      </div>

      <section className="catalog">
        {products.map((product) => (
          <article className="product" key={product.id}>
            <div className="row">
              <small className={`tag ${product.status}`}>{title(product.status)}</small>
              <strong>{money(product.currentPrice)}</strong>
            </div>

            <h3>{product.name}</h3>
            <label>
              {product.sku} / {product.category}
            </label>

            <dl>
              <div>
                <dt>Stock</dt>
                <dd className={product.stockLevel <= product.reorderThreshold ? "danger" : ""}>
                  {product.stockLevel} / {product.reorderThreshold}
                </dd>
              </div>
              <div>
                <dt>Demand velocity</dt>
                <dd>{product.demandVelocity} orders</dd>
              </div>
            </dl>

            <div className="actions three">
              <button disabled={busy === product.id} onClick={() => recordSale(product)}>
                Sale
              </button>
              <button disabled={busy === product.id} onClick={() => requestSuggestion(product, "pricing")}>
                Price rec
              </button>
              <button disabled={busy === product.id} onClick={() => requestSuggestion(product, "reorder")}>
                Reorder rec
              </button>
            </div>
          </article>
        ))}
      </section>
    </main>
  );
}
