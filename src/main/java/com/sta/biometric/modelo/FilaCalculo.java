package com.sta.biometric.modelo;

import java.math.*;

import javax.persistence.*;

import org.openxava.annotations.*;

import lombok.*;

/**
 * Representa una fila en la tabla de cálculos de horas.
 * Usado con @ElementCollection en AuditoriaRegistros.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FilaCalculo {

    /**
     * Tipo de hora: "⏰ Normales", "⏰+ Extras", "⭐ Especiales"
     * 
     */
	@ReadOnly
    private String tipo;

    /**
     * Valor hora (snapshot histórico)
     */
    @Money @ReadOnly
    private BigDecimal valorHora;

    /**
     * Horas registradas antes del ajuste (formato HH:MM)
     */
    @ReadOnly
    private String horasRegistradas;

    /**
     * Ajuste aplicado (formato +/-HH:MM)
     */
    @ReadOnly
    private String ajuste;

    /**
     * Horas enviadas al Banco de Horas (formato -HH:MM o +HH:MM, o "-")
     */
    @ReadOnly
    private String bancoHoras = "-";

    /**
     * Total monetario calculado
     */
    @Money
    @ReadOnly
    private BigDecimal total;

    /**
     * Constructor de compatibilidad (5 parámetros).
     */
    public FilaCalculo(String tipo, BigDecimal valorHora, String horasRegistradas, String ajuste, BigDecimal total) {
        this.tipo = tipo;
        this.valorHora = valorHora;
        this.horasRegistradas = horasRegistradas;
        this.ajuste = ajuste;
        this.bancoHoras = "-";
        this.total = total;
    }
}
