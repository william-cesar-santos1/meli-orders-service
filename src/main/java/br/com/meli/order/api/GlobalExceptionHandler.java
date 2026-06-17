package br.com.meli.order.api;

import br.com.meli.order.domain.exceptions.OrderNotFoundException;
import br.com.meli.order.domain.exceptions.OutOfStockException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Tracer tracer;

    public GlobalExceptionHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Registra a excecao no span atual sem marcar como ERROR (erros de cliente — 4xx).
     * O span fica visivelno Jaeger com o atributo "exception.type" para fins de debugging.
     */
    private void tagSpanWithClientError(Exception ex) {
        Span span = tracer.currentSpan();
        if (span != null) {
            span.tag("exception.type", ex.getClass().getSimpleName());
            span.tag("exception.message", ex.getMessage());
        }
    }

    /**
     * SOLUCAO (Erros no Jaeger): marca o span atual como ERROR e registra a excecao.
     * Sem esta chamada, o span e fechado como "OK" mesmo que a requisicao tenha falhado
     * com 5xx — os erros ficam invisiveis no Jaeger.
     *
     * span.error(ex) faz duas coisas:
     *   1. Define o status do span como ERROR (visivel como span vermelho no Jaeger).
     *   2. Registra o stack trace da excecao como evento no span.
     */
    private void markSpanAsError(Exception ex) {
        Span span = tracer.currentSpan();
        if (span != null) {
            span.error(ex);
        }
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ProblemDetail handleOrderNotFound(OrderNotFoundException ex) {
        tagSpanWithClientError(ex);   // 404 — erro de cliente, nao de servidor
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setTitle("Pedido não encontrado");
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(OutOfStockException.class)
    public ProblemDetail handleOutOfStock(OutOfStockException ex) {
        tagSpanWithClientError(ex);   // 409 — erro de cliente, nao de servidor
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setTitle("Produto sem estoque");
        detail.setDetail(ex.getMessage());
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        tagSpanWithClientError(ex);   // 400 — erro de cliente, nao de servidor
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setTitle("Dados inválidos");
        detail.setDetail(ex.getBindingResult().getAllErrors().get(0).getDefaultMessage());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        markSpanAsError(ex);          // 500 — erro de servidor: marca span como ERROR no Jaeger
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("Erro interno");
        detail.setDetail(ex.getMessage());
        return detail;
    }
}

