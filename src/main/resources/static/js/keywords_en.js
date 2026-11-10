$(document).ready(function () {
  // Initialize DataTable
  var table = new DataTable("#keywordsTable", {
    pageLength: 10,
    lengthMenu: [
      [10, 25, 50, 100],
      [10, 25, 50, 100],
    ],
    order: [[0, "asc"]],
    language: {
      lengthMenu: "Show _MENU_ entries",
      info: "Showing _START_ to _END_ of _TOTAL_ keywords",
      infoEmpty: "No keywords to display",
      infoFiltered: "(filtered from _MAX_ total keywords)",
      paginate: {
        first: "First",
        last: "Last",
        next: "Next",
        previous: "Previous",
      },
    },
    dom: 't<"table-controls-outside"lip>',
    paging: true,
    responsive: false,
  });

  // Move controls outside the table wrapper after DataTables initializes
  $(".table-controls-outside").insertAfter(".keywords-table-wrapper");

  // Custom search input
  $("#filterSearch").on("keyup", function () {
    table.search(this.value).draw();
  });

  // Filter functionality
  $("#filterType, #filterLanguage, #filterStatus").on("change", function () {
    var typeFilter = $("#filterType").val();
    var langFilter = $("#filterLanguage").val();
    var statusFilter = $("#filterStatus").val();

    table.column(1).search(langFilter).draw();
    table.column(2).search(typeFilter).draw();
    table.column(3).search(statusFilter).draw();
  });

  $("#clearFilters").on("click", function () {
    $("#filterType").val("");
    $("#filterLanguage").val("");
    $("#filterStatus").val("");
    $("#filterSearch").val("");
    table.search("").columns().search("").draw();
  });

  // Add new keyword button
  $("#addNewBtn").on("click", function () {
    $("#modalTitle").text("Add New Keyword");
    $("#editId").val("");
    $("#editWord").val("");
    $("#editLanguage").val("en");
    $("#editType").val("profanity");
    $("#editActive").prop("checked", true);
    $("#errorMessage").hide();
    $("#successMessage").hide();
    $("#editModal").addClass("show");
    setTimeout(function () {
      $("#editWord").focus();
    }, 300);
  });

  // Cancel button
  $("#cancelBtn").on("click", function () {
    $("#editModal").removeClass("show");
  });

  // Close modal on outside click
  $("#editModal").on("click", function (e) {
    if ($(e.target).hasClass("modal-overlay")) {
      $("#editModal").removeClass("show");
    }
  });

  // ESC key closes modal
  $(document).on("keyup", function (e) {
    if (e.key === "Escape" && $("#editModal").hasClass("show")) {
      $("#editModal").removeClass("show");
    }
  });

  // Edit button
  $("body").on("click", ".editBtn", function (e) {
    var btn = $(e.target).closest("button");
    $("#modalTitle").text("Edit Keyword");
    $("#editId").val(btn.data("id"));
    $("#editWord").val(btn.data("word"));
    $("#editLanguage").val(btn.data("language"));
    $("#editType").val(btn.data("type"));
    $("#editActive").prop("checked", btn.data("active"));
    $("#errorMessage").hide();
    $("#successMessage").hide();
    $("#editModal").addClass("show");
    setTimeout(function () {
      $("#editWord").focus();
    }, 300);
  });

  // Form submission
  $("#editForm").on("submit", function (e) {
    e.preventDefault();
    $("#errorMessage").hide();
    $("#successMessage").hide();

    var submitBtn = $(this).find("button[type=submit]");
    var originalText = submitBtn.html();
    submitBtn
      .prop("disabled", true)
      .html('<span class="spinner"></span> Saving...');

    var id = $("#editId").val();
    var url = id ? "/keywords/update" : "/keywords/create";
    var data = {
      word: $("#editWord").val(),
      language: $("#editLanguage").val(),
      type: $("#editType").val(),
      active: $("#editActive").is(":checked"),
    };

    if (id) {
      data.id = id;
    }

    $.ajax({
      type: "POST",
      data: data,
      cache: false,
      url: url,
      dataType: "text",
      error: function (xhr, status, error) {
        submitBtn.prop("disabled", false).html(originalText);
        $("#errorMessage")
          .html("<strong>Error:</strong> " + xhr.responseText)
          .show();
      },
      success: function (response) {
        submitBtn.prop("disabled", false).html(originalText);
        if (response.startsWith("Error")) {
          $("#errorMessage")
            .html("<strong>Error:</strong> " + response.replace("Error: ", ""))
            .show();
        } else {
          $("#successMessage")
            .html("<strong>Success!</strong> Keyword saved. Reloading...")
            .show();
          setTimeout(function () {
            location.reload();
          }, 1000);
        }
      },
    });
  });

  // Delete button
  $("body").on("click", ".deleteBtn", function (e) {
    var btn = $(e.target).closest("button");
    var word = btn.closest("tr").find("td:first").text().trim();

    if (
      !confirm(
        "Are you sure you want to delete the keyword '" +
          word +
          "'?\n\nThis action cannot be undone.",
      )
    ) {
      return;
    }

    var originalHtml = btn.html();
    btn.prop("disabled", true).html('<span class="spinner"></span>');

    var id = btn.attr("id").replace("delete", "");
    $.ajax({
      type: "POST",
      data: { id: id },
      cache: false,
      url: "/keywords/delete",
      dataType: "text",
      error: function (xhr, status, error) {
        btn.prop("disabled", false).html(originalHtml);
        alert("Error deleting keyword: " + xhr.responseText);
      },
      success: function (response) {
        if (response.startsWith("Error")) {
          btn.prop("disabled", false).html(originalHtml);
          alert(response);
        } else {
          var row = btn.closest("tr");
          row.css("background-color", "#f8d7da");
          row.fadeOut(400, function () {
            table.row(row).remove().draw();
          });
        }
      },
    });
  });

  // Activate button
  $("body").on("click", ".activateBtn", function (e) {
    var btn = $(e.target).closest("button");
    var originalHtml = btn.html();
    btn.prop("disabled", true).html('<span class="spinner"></span>');

    var id = btn.attr("id").replace("activate", "");
    $.ajax({
      type: "POST",
      data: { id: id, active: true },
      cache: false,
      url: "/keywords/update",
      dataType: "text",
      error: function (xhr, status, error) {
        btn.prop("disabled", false).html(originalHtml);
        alert("Error activating keyword: " + xhr.responseText);
      },
      success: function (response) {
        if (response.startsWith("Error")) {
          btn.prop("disabled", false).html(originalHtml);
          alert(response);
        } else {
          var row = btn.closest("tr");
          row
            .find("td:eq(3)")
            .html("<span class='tag tag-active'>ACTIVE</span>");
          btn.replaceWith(
            "<button id='deactivate" +
              id +
              "' class='btn-action btn-deactivate deactivateBtn' title='Deactivate keyword'>Deactivate</button>",
          );
        }
      },
    });
  });

  // Deactivate button
  $("body").on("click", ".deactivateBtn", function (e) {
    var btn = $(e.target).closest("button");
    var originalHtml = btn.html();
    btn.prop("disabled", true).html('<span class="spinner"></span>');

    var id = btn.attr("id").replace("deactivate", "");
    $.ajax({
      type: "POST",
      data: { id: id, active: false },
      cache: false,
      url: "/keywords/update",
      dataType: "text",
      error: function (xhr, status, error) {
        btn.prop("disabled", false).html(originalHtml);
        alert("Error deactivating keyword: " + xhr.responseText);
      },
      success: function (response) {
        if (response.startsWith("Error")) {
          btn.prop("disabled", false).html(originalHtml);
          alert(response);
        } else {
          var row = btn.closest("tr");
          row
            .find("td:eq(3)")
            .html("<span class='tag tag-inactive'>INACTIVE</span>");
          btn.replaceWith(
            "<button id='activate" +
              id +
              "' class='btn-action btn-activate activateBtn' title='Activate keyword'>Activate</button>",
          );
        }
      },
    });
  });

  // Export button
  $("#exportBtn").on("click", function () {
    window.location.href = "/keywords/export";
  });
});
