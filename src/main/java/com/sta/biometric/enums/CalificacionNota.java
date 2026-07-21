package com.sta.biometric.enums;

/**
 * Enum para calificar las notas de desempeño de un empleado.
 * Permite categorizar cada observación y calcular un promedio de desempeño.
 */
public enum CalificacionNota {
    BUENA(3),
    NORMAL(2),
    MALA(1);

    private final int peso;

    CalificacionNota(int peso) {
        this.peso = peso;
    }

    public int getPeso() {
        return peso;
    }
}
