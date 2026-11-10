var SafeRender = (function () {
  function escapeHtml(value) {
    return String(value == null ? "" : value).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function link(data, type) {
    if (type !== "display") return data;
    var text = escapeHtml(data);
    if (!/^https?:\/\//i.test(String(data == null ? "" : data).trim())) return text;
    return '<a href="' + text + '" target="_blank" rel="noopener noreferrer">' + text + "</a>";
  }

  return {
    link: link,
    columnDefs: [{ targets: "_all", render: DataTable.render.text() }],
  };
})();
