#!/usr/bin/env node
// Seeds the running backend (local H2 or Docker Compose Postgres) with the fixtures in
// this folder: registers users, promotes one to ADMIN, creates the product catalog,
// populates a few carts, replays a couple of checkout scenarios, and applies a few
// manual stock adjustments. Safe to re-run -- it treats "already exists" (409) as
// success and moves on.
//
// Usage:
//   node seed.js                                   # targets http://localhost:8080
//   API_BASE_URL=http://localhost:8080 node seed.js
//
// Promoting the admin user requires a running `db` Postgres container from the root
// docker-compose.yml (the script shells out to `docker compose exec db psql`). Against
// the local H2 profile, promote the seeded admin manually instead -- see the printed
// instructions if the docker step isn't available.

import { execSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import path from "node:path";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

function readJson(relativePath) {
  return JSON.parse(readFileSync(path.join(__dirname, relativePath), "utf-8"));
}

async function api(method, endpoint, body, token) {
  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : null;
  return { status: response.status, data };
}

async function registerUser(user) {
  const { status, data } = await api("POST", "/api/auth/register", {
    email: user.email,
    password: user.password,
    fullName: user.fullName,
  });
  if (status === 201) {
    console.log(`  created ${user.email}`);
  } else if (status === 409) {
    console.log(`  already exists ${user.email}`);
  } else {
    console.warn(`  unexpected status ${status} registering ${user.email}:`, data);
  }
}

async function login(email, password) {
  const { status, data } = await api("POST", "/api/auth/login", { email, password });
  if (status !== 200) throw new Error(`login failed for ${email}: ${status} ${JSON.stringify(data)}`);
  return data.accessToken;
}

function promoteToAdmin(email) {
  try {
    execSync(
      `docker compose exec -T db psql -U ecommerce_user -d ecommercedb -c "UPDATE users SET role='ADMIN' WHERE email='${email}';"`,
      { cwd: path.join(__dirname, ".."), stdio: "pipe" },
    );
    console.log(`  promoted ${email} to ADMIN via docker compose`);
    return true;
  } catch {
    console.warn(
      `  could not reach the 'db' container to promote ${email} to ADMIN automatically.\n` +
        `  Run this yourself once the stack is up:\n` +
        `    docker compose exec db psql -U ecommerce_user -d ecommercedb -c "UPDATE users SET role='ADMIN' WHERE email='${email}';"\n` +
        `  Or, against the local H2 profile, run the equivalent UPDATE in the H2 console\n` +
        `  (http://localhost:8080/h2-console, JDBC URL jdbc:h2:mem:ecommercedb).`,
    );
    return false;
  }
}

async function main() {
  const { users } = readJson("users/users.json");
  const { products } = readJson("products/products.json");
  const { carts } = readJson("carts/carts.json");
  const { scenarios } = readJson("orders/orders.json");
  const { adjustments } = readJson("inventory/inventory-adjustments.json");

  console.log(`Seeding ${API_BASE_URL} ...\n`);

  console.log("Registering users:");
  for (const user of users) {
    await registerUser(user);
  }

  const admin = users.find((u) => u.role === "ADMIN");
  console.log(`\nPromoting ${admin.email} to ADMIN:`);
  const promoted = promoteToAdmin(admin.email);

  console.log("\nCreating product catalog:");
  const skuToId = {};
  let adminToken = null;
  if (promoted) {
    adminToken = await login(admin.email, admin.password);
    for (const product of products) {
      const { description, edgeCase, ...body } = product;
      void edgeCase;
      const { status, data } = await api("POST", "/api/products", { ...body, description }, adminToken);
      if (status === 201) {
        skuToId[product.sku] = data.id;
        console.log(`  created ${product.sku} -> id ${data.id}`);
      } else if (status === 409) {
        console.log(`  already exists ${product.sku}`);
      } else {
        console.warn(`  unexpected status ${status} creating ${product.sku}:`, data);
      }
    }
  } else {
    console.log("  skipped (admin account isn't ADMIN yet -- see instructions above)");
  }

  console.log("\nPopulating carts:");
  for (const cart of carts) {
    const user = users.find((u) => u.email === cart.owner);
    if (!user || cart.items.length === 0) {
      console.log(`  skipping ${cart.owner} (no items)`);
      continue;
    }
    const token = await login(user.email, user.password);
    for (const item of cart.items) {
      const productId = skuToId[item.sku];
      if (!productId) continue;
      await api("POST", "/api/cart/items", { productId, quantity: item.quantity }, token);
    }
    console.log(`  populated cart for ${cart.owner}`);
  }

  console.log("\nReplaying checkout scenarios:");
  for (const scenario of scenarios) {
    const user = users.find((u) => u.email === scenario.owner);
    if (!user) continue;
    const token = await login(user.email, user.password);
    const { status, data } = await api(
      "POST",
      "/api/orders/checkout",
      { paymentMethodToken: scenario.paymentMethodToken },
      token,
    );
    if (status === 200) {
      console.log(`  checked out for ${scenario.owner} -> order #${data.id} (${data.status})`);
    } else {
      console.warn(`  checkout failed for ${scenario.owner}: ${status}`, data);
    }
  }

  if (adminToken) {
    console.log("\nApplying inventory adjustments:");
    for (const adj of adjustments) {
      const productId = skuToId[adj.sku];
      if (!productId) continue;
      const { status } = await api(
        "PATCH",
        `/api/products/${productId}/stock`,
        { quantityChange: adj.quantityChange, reason: adj.reason },
        adminToken,
      );
      console.log(`  ${status === 200 ? "applied" : `failed (${status})`} adjustment for ${adj.sku}`);
    }
  }

  console.log("\nDone.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
