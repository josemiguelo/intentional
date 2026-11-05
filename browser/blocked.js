// Names the site when the extension moved a tab here (?site=…); a redirected
// visit arrives without it.
const site = new URLSearchParams(location.search).get("site");
if (site) document.getElementById("title").textContent = `${site} · budget used today`;
