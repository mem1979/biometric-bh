<div id="beta">BETA</div>

<script type="text/javascript">
(function() {
    function fixDropdowns() {
        var dropdowns = document.querySelectorAll('.dropdown-toggle, [data-bs-toggle="dropdown"], [data-toggle="dropdown"]');
        for (var i = 0; i < dropdowns.length; i++) {
            dropdowns[i].classList.remove('xava_action');
        }
    }

    // Run immediately when loaded
    fixDropdowns();

    // Run on window load
    window.addEventListener('load', fixDropdowns);

    // Monitor DOM changes to apply the fix dynamically after AJAX refreshes
    if (window.MutationObserver) {
        var observer = new MutationObserver(function(mutations) {
            fixDropdowns();
        });
        observer.observe(document.body, { childList: true, subtree: true });
    } else {
        // Fallback for older browsers
        setInterval(fixDropdowns, 500);
    }
})();
</script>