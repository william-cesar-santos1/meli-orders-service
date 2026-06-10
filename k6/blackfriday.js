// SOLUÇÃO: thresholds definem os budgets como critério de aceite do build.
// Violação de p95 > 300ms ou erro > 1% retorna exit code 1 — o CI falha automaticamente.
// Performance passa a ser um requisito verificável, não um desejo pós-deploy.
import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
    stages: [
        { duration: '3m', target: 1000 }, // ramp-up: simula abertura de campanha
        { duration: '5m', target: 1000 }, // plateau: valida estabilidade sob carga sustentada
        { duration: '1m', target: 0 },    // ramp-down: verifica recuperação sem degradação
    ],
    thresholds: {
        'http_req_duration': ['p(95)<300'],
        'http_req_failed':   ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
    const customerId = `customer-${Math.floor(Math.random() * 10000)}`;

    const res = http.post(
        `${BASE_URL}/orders`,
        JSON.stringify({
            customerId: customerId,
            items: [
                {
                    productId: 'prod-tenis',
                    quantity: 1,
                    unitPrice: 350.00,
                    productName: 'Tênis Nike Air Max'
                }
            ]
        }),
        {
            headers: {
                'Content-Type': 'application/json',
                'Idempotency-Key': `${customerId}-${Date.now()}`,
            },
        }
    );

    check(res, {
        'order created': (r) => r.status === 201,
        'within budget': (r) => r.timings.duration < 300,
    });

    sleep(0.05);
}

