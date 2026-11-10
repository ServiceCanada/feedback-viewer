/**
 * DataTable Loading Overlay Manager
 * Provides a modern, full-page loading indicator for DataTables filtering and processing
 *
 * Usage:
 *   const loadingOverlay = new DataTableLoadingOverlay(options);
 *   loadingOverlay.show();
 *   loadingOverlay.hide();
 *
 * Or use the factory function:
 *   const overlay = createDataTableLoadingOverlay(options);
 */

class DataTableLoadingOverlay {
  constructor(options = {}) {
    this.options = {
      loadingText: options.loadingText || "Loading data...",
      subtext: options.subtext || "Please wait while we filter your results",
      timeoutMessage:
        options.timeoutMessage ||
        "This is taking longer than expected. You can close this dialog if needed.",
      spinnerType: options.spinnerType || "spinner", // 'spinner' or 'dots'
      zIndex: options.zIndex || 9999,
      showTimeoutAfter: options.showTimeoutAfter || 8000, // Show timeout message after 8 seconds
      allowDismiss: options.allowDismiss !== false, // Allow dismissing by default
      miniLoadingText: options.miniLoadingText || "Loading...",
      ...options,
    };

    this.overlay = null;
    this.miniIndicator = null;
    this.isVisible = false;
    this.isLoading = false;
    this.timeoutTimer = null;
    this.init();
  }

  /**
   * Initialize the overlay and inject it into the DOM
   */
  init() {
    const existingOverlay = document.getElementById(
      "datatable-loading-overlay",
    );
    if (existingOverlay) {
      this.overlay = existingOverlay;
      // Re-attach close button handler if it exists
      const closeBtn = this.overlay.querySelector(".datatable-loading-close");
      if (closeBtn) {
        closeBtn.onclick = (e) => {
          e.preventDefault();
          e.stopPropagation();
          this.hideFull();
        };
      }
    } else {
      this.overlay = this.createOverlay();
      document.body.appendChild(this.overlay);
    }

    // Initialize mini indicator
    const existingMini = document.getElementById("datatable-loading-mini");
    if (existingMini) {
      this.miniIndicator = existingMini;
    } else {
      this.miniIndicator = this.createMiniIndicator();
      document.body.appendChild(this.miniIndicator);
    }
  }

  /**
   * Create the overlay DOM structure
   */
  createOverlay() {
    const overlay = document.createElement("div");
    overlay.id = "datatable-loading-overlay";
    overlay.className = "datatable-loading-overlay";
    overlay.setAttribute("role", "alert");
    overlay.setAttribute("aria-live", "assertive");
    overlay.setAttribute("aria-busy", "true");
    overlay.style.zIndex = this.options.zIndex;

    const content = document.createElement("div");
    content.className = "datatable-loading-content";

    // Add close button if dismissable
    if (this.options.allowDismiss) {
      const closeBtn = document.createElement("button");
      closeBtn.className = "datatable-loading-close";
      closeBtn.innerHTML = "&times;";
      closeBtn.setAttribute("aria-label", "Close loading overlay");
      closeBtn.setAttribute("title", "Close");
      closeBtn.setAttribute("type", "button");
      closeBtn.onclick = (e) => {
        e.preventDefault();
        e.stopPropagation();
        this.hideFull();
      };
      content.appendChild(closeBtn);
    }

    // Create spinner
    const spinner = document.createElement("div");
    spinner.className = "datatable-loading-spinner";
    spinner.setAttribute("aria-hidden", "true");
    content.appendChild(spinner);

    // Add text
    const text = document.createElement("div");
    text.className = "datatable-loading-text";
    text.textContent = this.options.loadingText;
    content.appendChild(text);

    // Add subtext if provided
    if (this.options.subtext) {
      const subtext = document.createElement("div");
      subtext.className = "datatable-loading-subtext";
      subtext.textContent = this.options.subtext;
      content.appendChild(subtext);
    }

    // Add timeout message (initially hidden)
    const timeoutMsg = document.createElement("div");
    timeoutMsg.className = "datatable-loading-timeout";
    timeoutMsg.textContent = this.options.timeoutMessage;
    content.appendChild(timeoutMsg);

    // Add progress bar (initially hidden)
    const progressContainer = document.createElement("div");
    progressContainer.className = "datatable-loading-progress";
    const progressBar = document.createElement("div");
    progressBar.className = "datatable-loading-progress-bar";
    progressContainer.appendChild(progressBar);
    content.appendChild(progressContainer);

    overlay.appendChild(content);
    return overlay;
  }

  /**
   * Create the mini indicator DOM structure
   */
  createMiniIndicator() {
    const mini = document.createElement("div");
    mini.id = "datatable-loading-mini";
    mini.className = "datatable-loading-mini";
    mini.setAttribute("role", "status");
    mini.setAttribute("aria-live", "polite");

    const spinner = document.createElement("div");
    spinner.className = "datatable-loading-mini-spinner";
    spinner.setAttribute("aria-hidden", "true");
    mini.appendChild(spinner);

    const text = document.createElement("div");
    text.className = "datatable-loading-mini-text";
    text.textContent = this.options.miniLoadingText;
    mini.appendChild(text);

    return mini;
  }

  /**
   * Show the loading overlay
   */
  show() {
    this.isLoading = true;

    if (this.overlay && !this.isVisible) {
      // Reset timeout message
      const timeoutMsg = this.overlay.querySelector(
        ".datatable-loading-timeout",
      );
      const progressBar = this.overlay.querySelector(
        ".datatable-loading-progress",
      );
      if (timeoutMsg) timeoutMsg.classList.remove("show");
      if (progressBar) progressBar.classList.remove("show");

      // Use requestAnimationFrame for smoother rendering
      requestAnimationFrame(() => {
        this.overlay.classList.add("active");
        this.isVisible = true;
        document.body.style.overflow = "hidden"; // Prevent scrolling
      });

      // Set timeout to show warning message
      if (this.options.showTimeoutAfter > 0) {
        this.timeoutTimer = setTimeout(() => {
          if (this.isVisible) {
            if (timeoutMsg) timeoutMsg.classList.add("show");
            if (progressBar) progressBar.classList.add("show");
          }
        }, this.options.showTimeoutAfter);
      }
    }
  }

  /**
   * Hide only the full overlay (show mini indicator instead)
   */
  hideFull() {
    if (this.overlay && this.isVisible) {
      // Clear timeout timer
      if (this.timeoutTimer) {
        clearTimeout(this.timeoutTimer);
        this.timeoutTimer = null;
      }

      this.overlay.classList.remove("active");
      this.isVisible = false;
      document.body.style.overflow = ""; // Restore scrolling

      // Show mini indicator if still loading
      if (this.isLoading && this.miniIndicator) {
        this.miniIndicator.classList.add("active");
      }
    }
  }

  /**
   * Hide the loading overlay (and mini indicator)
   */
  hide() {
    this.isLoading = false;

    if (this.overlay && this.isVisible) {
      // Clear timeout timer
      if (this.timeoutTimer) {
        clearTimeout(this.timeoutTimer);
        this.timeoutTimer = null;
      }

      this.overlay.classList.remove("active");
      this.isVisible = false;
      document.body.style.overflow = ""; // Restore scrolling
    }

    // Hide mini indicator
    if (this.miniIndicator) {
      this.miniIndicator.classList.remove("active");
    }
  }

  /**
   * Update the loading text dynamically
   */
  updateText(newText, newSubtext = null) {
    const textElement = this.overlay.querySelector(".datatable-loading-text");
    if (textElement) {
      textElement.textContent = newText;
    }

    if (newSubtext !== null) {
      const subtextElement = this.overlay.querySelector(
        ".datatable-loading-subtext",
      );
      if (subtextElement) {
        subtextElement.textContent = newSubtext;
      }
    }
  }

  /**
   * Destroy the overlay and remove from DOM
   */
  destroy() {
    if (this.overlay) {
      this.hide();
      this.overlay.remove();
      this.overlay = null;
    }
    if (this.miniIndicator) {
      this.miniIndicator.remove();
      this.miniIndicator = null;
    }
  }
}

/**
 * Factory function to create and attach loading overlay to a DataTable
 * @param {DataTable} table - The DataTables instance
 * @param {Object} options - Configuration options
 * @returns {DataTableLoadingOverlay} The overlay instance
 */
function attachLoadingOverlay(table, options = {}) {
  const overlay = new DataTableLoadingOverlay(options);

  // Hook into DataTables events
  table.on("processing.dt", function (e, settings, processing) {
    if (processing) {
      overlay.show();
    } else {
      overlay.hide();
    }
  });

  // Ensure overlay is hidden on table initialization complete
  table.on("init.dt", function () {
    overlay.hide();
  });

  // Handle errors - hide overlay if AJAX fails
  table.on("error.dt", function () {
    overlay.hide();
  });

  return overlay;
}

/**
 * Convenience function to create overlay with default settings
 */
function createDataTableLoadingOverlay(
  isFrench = false,
  spinnerType = "spinner",
) {
  return new DataTableLoadingOverlay({
    loadingText: isFrench ? "Chargement des données..." : "Loading data...",
    subtext: isFrench
      ? "Veuillez patienter pendant que nous filtrons vos résultats"
      : "Please wait while we filter your results",
    timeoutMessage: isFrench
      ? "Cela prend plus de temps que prévu. Vous pouvez fermer cette fenêtre si nécessaire."
      : "This is taking longer than expected. You can close this dialog if needed.",
    miniLoadingText: isFrench ? "Chargement..." : "Loading...",
    spinnerType: spinnerType,
    showTimeoutAfter: 8000, // Show message after 8 seconds
    allowDismiss: true,
  });
}

// Export for use in other scripts
if (typeof module !== "undefined" && module.exports) {
  module.exports = {
    DataTableLoadingOverlay,
    attachLoadingOverlay,
    createDataTableLoadingOverlay,
  };
}
