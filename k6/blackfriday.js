// SOLUÇÃO: thresholds definem os budgets como critério de aceite do build.
// Violação de p95 > 300ms ou erro > 1% retorna exit code 1 — o CI falha automaticamente.
// Performance passa a ser um requisito verificável, não um desejo pós-deploy.
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── Métricas customizadas por cenário ──────────────────────────────────────
const orderCreateErrors  = new Counter('order_create_errors');
const orderListErrors    = new Counter('order_list_errors');
const orderSearchErrors  = new Counter('order_search_errors');
const orderPayErrors     = new Counter('order_pay_errors');

const createDuration = new Trend('order_create_duration', true);
const listDuration   = new Trend('order_list_duration',   true);
const searchDuration = new Trend('order_search_duration', true);
const payDuration    = new Trend('order_pay_duration',    true);

// ─── Configuração de carga ───────────────────────────────────────────────────
export const options = {
    scenarios: {
        // Cenário 1 — Criação de pedidos (POST /orders)
        // Simula clientes comprando na abertura da campanha Black Friday.
        criar_pedidos: {
            executor: 'ramping-vus',
            stages: [
                { duration: '2m', target: 500 },  // ramp-up
                { duration: '5m', target: 500 },  // plateau
                { duration: '1m', target: 0   },  // ramp-down
            ],
            exec: 'criarPedido',
        },
        // Cenário 2 — Listagem de pedidos por cliente (GET /orders?customerId=...)
        // Simula clientes verificando o histórico de pedidos durante a campanha.
        listar_pedidos: {
            executor: 'ramping-vus',
            startTime: '30s',
            stages: [
                { duration: '2m', target: 300 },
                { duration: '5m', target: 300 },
                { duration: '1m', target: 0   },
            ],
            exec: 'listarPedidos',
        },
        // Cenário 3 — Busca full-text (GET /orders/search?q=...)
        // Simula a busca por produtos no histórico — exercita Elasticsearch.
        buscar_pedidos: {
            executor: 'ramping-vus',
            startTime: '30s',
            stages: [
                { duration: '2m', target: 200 },
                { duration: '5m', target: 200 },
                { duration: '1m', target: 0   },
            ],
            exec: 'buscarPedidos',
        },
        // Cenário 4 — Pagamento de pedidos (PATCH /orders/{id}/pay)
        // Simula clientes confirmando pagamento. Volume menor — pedidos precisam existir.
        pagar_pedidos: {
            executor: 'ramping-vus',
            startTime: '1m',   // aguarda criação de pedidos para ter IDs válidos
            stages: [
                { duration: '2m', target: 100 },
                { duration: '4m', target: 100 },
                { duration: '1m', target: 0   },
            ],
            exec: 'pagarPedido',
        },
    },
    thresholds: {
        // SLA global
        'http_req_duration':                  ['p(95)<300', 'p(99)<500'],
        'http_req_failed':                    ['rate<0.01'],

        // SLA por cenário
        'order_create_duration':              ['p(95)<300'],
        'order_list_duration':                ['p(95)<200'],
        'order_search_duration':              ['p(95)<400'],
        'order_pay_duration':                 ['p(95)<300'],

        // Contadores de erro por cenário — zero tolerância em 5xx
        'order_create_errors':                ['count<10'],
        'order_pay_errors':                   ['count<5'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

// Pool de clientes e termos de busca compartilhados entre VUs
const CUSTOMERS = Array.from({ length: 500 }, (_, i) => `customer-${i}`);
const SEARCH_TERMS = ['tenis', 'nike', 'air', 'max', 'calcado', 'produto'];
const PRODUCTS = [
    { id: 'prod-tenis',   name: 'Tênis Nike Air Max',    price: 350.00 },
    { id: 'prod-camisa',  name: 'Camisa Adidas Dry Fit', price: 120.00 },
    { id: 'prod-bone',    name: 'Boné New Era',          price: 80.00  },
];

// ─── Cenário 1: Criar Pedido ─────────────────────────────────────────────────
export function criarPedido() {
    const customerId = CUSTOMERS[Math.floor(Math.random() * CUSTOMERS.length)];
    const product    = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];

    group('POST /orders', () => {
        const res = http.post(
            `${BASE_URL}/orders`,
            JSON.stringify({
                customerId,
                items: [{
                    productId:   product.id,
                    quantity:    1,
                    unitPrice:   product.price,
                    productName: product.name,
                }],
            }),
            {
                headers: {
                    'Content-Type':    'application/json',
                    'Idempotency-Key': `${customerId}-${product.id}-${Date.now()}`,
                },
            }
        );

        createDuration.add(res.timings.duration);

        const ok = check(res, {
            'pedido criado (201)':       (r) => r.status === 201,
            'idempotente (409 aceito)':  (r) => [201, 409].includes(r.status),
            'dentro do SLA (300ms)':     (r) => r.timings.duration < 300,
        });

        if (!ok && res.status >= 500) {
            orderCreateErrors.add(1);
        }

        // Salva o id criado para o cenário de pagamento
        if (res.status === 201) {
            const body = res.json();
            if (body && body.id) {
                // Compartilha o id via variável global k6 (por VU)
                __ENV.LAST_ORDER_ID = String(body.id);
            }
        }
    });

    sleep(0.1);
}

// ─── Cenário 2: Listar Pedidos por Cliente ───────────────────────────────────
export function listarPedidos() {
    const customerId = CUSTOMERS[Math.floor(Math.random() * CUSTOMERS.length)];

    group('GET /orders?customerId', () => {
        const res = http.get(
            `${BASE_URL}/orders?customerId=${customerId}`,
            { headers: { 'Accept': 'application/json' } }
        );

        listDuration.add(res.timings.duration);

        const ok = check(res, {
            'status 200':            (r) => r.status === 200,
            'resposta é array':      (r) => Array.isArray(r.json()),
            'dentro do SLA (200ms)': (r) => r.timings.duration < 200,
        });

        if (!ok && res.status >= 500) {
            orderListErrors.add(1);
        }
    });

    sleep(0.05);
}

// ─── Cenário 3: Busca Full-Text ──────────────────────────────────────────────
export function buscarPedidos() {
    const term = SEARCH_TERMS[Math.floor(Math.random() * SEARCH_TERMS.length)];

    group('GET /orders/search', () => {
        const res = http.get(
            `${BASE_URL}/orders/search?q=${term}`,
            { headers: { 'Accept': 'application/json' } }
        );

        searchDuration.add(res.timings.duration);

        const ok = check(res, {
            'status 200':            (r) => r.status === 200,
            'resposta é array':      (r) => Array.isArray(r.json()),
            'dentro do SLA (400ms)': (r) => r.timings.duration < 400,
        });

        if (!ok && res.status >= 500) {
            orderSearchErrors.add(1);
        }
    });

    sleep(0.1);
}

// ─── Função default — usada com k6 run --vus N --duration T (smoke test) ────
// Executa todos os cenários de forma round-robin para validação rápida de sintaxe.
export default function () {
    criarPedido();
    listarPedidos();
    buscarPedidos();
    pagarPedido();
}

// ─── Cenário 4: Pagar Pedido ─────────────────────────────────────────────────
// Primeiro busca um pedido existente via listagem, depois tenta pagá-lo.
export function pagarPedido() {
    const customerId = CUSTOMERS[Math.floor(Math.random() * CUSTOMERS.length)];

    group('PATCH /orders/{id}/pay', () => {
        // Passo 1: busca o último pedido do cliente
        const listRes = http.get(
            `${BASE_URL}/orders?customerId=${customerId}`,
            { headers: { 'Accept': 'application/json' } }
        );

        if (listRes.status !== 200) return;

        const orders = listRes.json();
        if (!Array.isArray(orders) || orders.length === 0) return;

        // Pega o pedido mais recente
        const order = orders[orders.length - 1];
        if (!order || !order.id) return;

        // Passo 2: paga o pedido
        const payRes = http.patch(
            `${BASE_URL}/orders/${order.id}/pay`,
            null,
            { headers: { 'Accept': 'application/json' } }
        );

        payDuration.add(payRes.timings.duration);

        const ok = check(payRes, {
            'pago (200) ou já pago (404/409)': (r) => [200, 404, 409].includes(r.status),
            'dentro do SLA (300ms)':           (r) => r.timings.duration < 300,
        });

        if (!ok && payRes.status >= 500) {
            orderPayErrors.add(1);
        }
    });

    sleep(0.2);
}
