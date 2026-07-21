package com.sta.biometric.dashboard.auxiliares;
import com.sta.biometric.enums.*;

import lombok.*;

@Getter @Setter
public class LicenciaResumenPorTipo {

    private int periodo;
    private TipoLicenciaAR tipo;
    private int dias;
    private int diasRestantes;

    public LicenciaResumenPorTipo() {}

    public LicenciaResumenPorTipo(TipoLicenciaAR tipo, int dias, int diasRestantes) {
        this.tipo = tipo;
        this.dias = dias;
        this.diasRestantes = diasRestantes;
    }

    public LicenciaResumenPorTipo(int periodo, TipoLicenciaAR tipo, int dias, int diasRestantes) {
        this.periodo = periodo;
        this.tipo = tipo;
        this.dias = dias;
        this.diasRestantes = diasRestantes;
    }
}