package com.sta.biometric.calculadores;

import javax.persistence.*;

import org.openxava.calculators.*;
import org.openxava.jpa.*;

public class GeneradorCodigoUserIdCalculator implements ICalculator {

    private static final long serialVersionUID = 1L;

    @Override
    public Object calculate() throws Exception {
        // 1. Obtener el último número de usuarios ACTIVOS (formato A...)
        Query queryActivos = XPersistence.getManager().createQuery(
                "select max(cast(substring(p.userId, 2) as integer)) " +
                        "from Personal p where p.userId like 'A%'");
        Integer ultimoActivo = (Integer) queryActivos.getSingleResult();
        int maxActivo = (ultimoActivo == null) ? 0 : ultimoActivo;

        // 2. Obtener el último número de usuarios INACTIVOS (formato x-A...)
        // 'x-A' son 3 caracteres, el número empieza en la posición 4
        Query queryInactivos = XPersistence.getManager().createQuery(
                "select max(cast(substring(p.userId, 4) as integer)) " +
                        "from Personal p where p.userId like 'x-A%'");
        Integer ultimoInactivo = (Integer) queryInactivos.getSingleResult();
        int maxInactivo = (ultimoInactivo == null) ? 0 : ultimoInactivo;

        // 3. Tomar el mayor de ambos y sumar 1
        int nuevoNumero = Math.max(maxActivo, maxInactivo) + 1;

        return "A" + nuevoNumero;
    }
}