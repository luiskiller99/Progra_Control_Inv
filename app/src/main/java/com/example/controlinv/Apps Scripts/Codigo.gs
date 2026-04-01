
function onEdit(e) {
  if (!e || !e.range) return;

  const sheet = e.source.getActiveSheet();
  const row = e.range.getRow();

  if (row === 1) return;

  const apiKey = "sb_publishable_uqTqwniA0xDwlmJQdD-7Dw_U1WROAdk";
  const baseUrl = "https://dsargvagjnoobyfkmait.supabase.co/rest/v1/inventario";

  const props = PropertiesService.getScriptProperties();
  if (props.getProperty("SYNC_RUNNING") === "true") return;

  props.setProperty("SYNC_RUNNING", "true");

  try {

    // 🔹 UPDATE FILA EDITADA
    const data = sheet.getRange(row, 1, 1, sheet.getLastColumn()).getValues()[0];
    const headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];

    let objeto = {};
    headers.forEach((h, i) => objeto[h] = data[i]);

    const id = (objeto.id || "").toString().trim();

    if (id) {
      const url = `${baseUrl}?id=eq.${id}`;

      UrlFetchApp.fetch(url, {
        method: "patch",
        contentType: "application/json",
        headers: {
          "apikey": apiKey,
          "Authorization": "Bearer " + apiKey,
          "Prefer": "return=minimal"
        },
        payload: JSON.stringify(objeto),
        muteHttpExceptions: true
      });
    }

    // 🔹 RECARGAR DESDE SUPABASE (OPTIMIZADO)
    const response = UrlFetchApp.fetch(baseUrl, {
      method: "get",
      headers: {
        "apikey": apiKey,
        "Authorization": "Bearer " + apiKey
      }
    });

    const dataSupabase = JSON.parse(response.getContentText());

    if (!dataSupabase || dataSupabase.length === 0) return;

    const headersSupabase = Object.keys(dataSupabase[0]);

    const values = dataSupabase.map(item =>
      headersSupabase.map(h => item[h] ?? "")
    );

    sheet.clearContents();

    sheet.getRange(1, 1, 1, headersSupabase.length).setValues([headersSupabase]);
    sheet.getRange(2, 1, values.length, headersSupabase.length).setValues(values);

  } catch (err) {
    // Logger.log("ERROR: " + err);
  }

  props.deleteProperty("SYNC_RUNNING");
}
function cargarDatos() {
  const url = "https://dsargvagjnoobyfkmait.supabase.co/rest/v1/inventario";
  const apiKey = "sb_publishable_uqTqwniA0xDwlmJQdD-7Dw_U1WROAdk";

  const response = UrlFetchApp.fetch(url, {
    method: "get",
    headers: {
      "apikey": apiKey,
      "Authorization": "Bearer " + apiKey
    }
  });

  const data = JSON.parse(response.getContentText());

  const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();

  if (!data || data.length === 0) return;

  const headers = Object.keys(data[0]);
  const values = data.map(item => headers.map(h => item[h] ?? ""));

  sheet.clearContents();

  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  sheet.getRange(2, 1, values.length, headers.length).setValues(values);
}
function syncInventario() {
  const sheet = SpreadsheetApp.getActiveSpreadsheet().getActiveSheet();
  const data = sheet.getDataRange().getValues();

  const headers = data[0];
  const rows = data.slice(1);

  const apiKey = "sb_publishable_uqTqwniA0xDwlmJQdD-7Dw_U1WROAdk";
  const baseUrl = "https://dsargvagjnoobyfkmait.supabase.co/rest/v1/inventario";

  rows.forEach((row, index) => {

    let objeto = {};
    headers.forEach((h, i) => objeto[h] = row[i]);

    const id = (objeto.id || "").toString().trim();

    if (!id) return;

    const url = `${baseUrl}?id=eq.${id}`;

    UrlFetchApp.fetch(url, {
      method: "patch",
      contentType: "application/json",
      headers: {
        "apikey": apiKey,
        "Authorization": "Bearer " + apiKey,
        "Prefer": "return=representation"
      },
      payload: JSON.stringify(objeto),
      muteHttpExceptions: true
    });
  });

  // 🔹 REFRESCAR DESDE SUPABASE (OPTIMIZADO)
  const response = UrlFetchApp.fetch(baseUrl, {
    method: "get",
    headers: {
      "apikey": apiKey,
      "Authorization": "Bearer " + apiKey
    }
  });

  const dataSupabase = JSON.parse(response.getContentText());

  if (!dataSupabase || dataSupabase.length === 0) return;

  const headersSupabase = Object.keys(dataSupabase[0]);

  const values = dataSupabase.map(item =>
    headersSupabase.map(h => item[h] ?? "")
  );

  sheet.clearContents();

  sheet.getRange(1, 1, 1, headersSupabase.length).setValues([headersSupabase]);
  sheet.getRange(2, 1, values.length, headersSupabase.length).setValues(values);
}