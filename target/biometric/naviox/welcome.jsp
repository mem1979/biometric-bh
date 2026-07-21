<%@include file="../xava/imports.jsp" %>
    <!DOCTYPE html>
    <html lang="es">

    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Bienvenido a STA.RH</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/xava/style/custom.css">
    </head>

    <body class="welcome-body">

        <!-- Panel Izquierdo - Branding -->
        <div class="welcome-left">

            <!-- Logo -->
            <div class="welcome-logo-box">
                <img src="<%=request.getContextPath()%>/naviox/images/starh-logo.jpg" width="110" height="110"
                    alt="STA.RH Logo">
            </div>

            <!-- Titulo -->
            <h1 class="welcome-title">STA.RH</h1>

            <!-- Subtitulo -->
            <p class="welcome-subtitle">Soluciones Tecnologicas de Avanzada</p>

            <!-- Slogan -->
            <p class="welcome-slogan">
                <strong>Tome el control hoy.</strong><br>
                Audite el pasado, gestione el presente<br>
                y planifique el futuro.
            </p>
        </div>

        <!-- Panel Derecho - Login -->
        <div class="welcome-right">

            <div class="welcome-content-wrapper">
                <h2 class="welcome-heading">Bienvenido</h2>
                <p class="welcome-desc">Sistema de Gesti&oacute;n de Recursos Humanos</p>

                <button id="welcome_go_signin" class="welcome-btn">
                    Iniciar Sesi&oacute;n
                </button>

                <!-- QR Code Section -->
                <div class="welcome-qr-section">
                    <img src="<%=request.getContextPath()%>/naviox/images/descarga-app-STAenTurno.png"
                        class="welcome-qr-img" alt="Descargar App Android">
                    <p class="welcome-qr-text">
                        Optimice su gesti&oacute;n: Escanee este c&oacute;digo QR para descargar e instalar la App de
                        Registros de
                        Asistencia STAenTurno en dispositivos Android.
                    </p>
                </div>
            </div>

            <p class="welcome-footer">
                Desarrollado por <strong>S.T.A.</strong><br>
                <span class="welcome-footer-small">by M.E.M.</span>
            </p>
        </div>

        <script type="text/javascript" <xava:nonce />>
        document.getElementById('welcome_go_signin').onclick = function() {
        window.location = 'm/SignIn';
        };
        </script>
    </body>

    </html>