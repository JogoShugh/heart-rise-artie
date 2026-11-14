// Renders the map's icons once, to icons.json, so the deck build stays synchronous.
const fs = require("fs");
const React = require("react"), { renderToStaticMarkup } = require("react-dom/server"), sharp = require("sharp");
const si = require("react-icons/si"), fi = require("react-icons/fi"), fa = require("react-icons/fa");
const ICON = { md: si.SiMarkdown, claude: si.SiClaude, yaml: si.SiYaml, globe: fi.FiGlobe, ts: si.SiTypescript, mcp: si.SiModelcontextprotocol,
  compass: fi.FiCompass, file: fi.FiFileText, layers: fi.FiLayers, git: si.SiGit, info: fi.FiInfo, toggle: fi.FiToggleRight, slash: fi.FiSlash,
  shield: fi.FiShield, aws: fa.FaAws, server: fi.FiServer, jenkins: si.SiJenkins, gitlab: si.SiGitlab, github: si.SiGithub,
  cucumber: si.SiCucumber, x: fi.FiXCircle, code: fi.FiCode, check: fi.FiCheckCircle, commit: fi.FiGitCommit, bell: fi.FiBell, heart: fa.FaHeart };
const I = {};
const iconPng = async (k, color) => { const svg = renderToStaticMarkup(React.createElement(ICON[k], { color: "#" + color, size: 256 })); I[k + color] = "image/png;base64," + (await sharp(Buffer.from(svg)).png().toBuffer()).toString("base64"); };
(async () => {
const NEED = [["md","EDF2EE"],["claude","D97757"],["yaml","EDF2EE"],["globe","EDF2EE"],["ts","EDF2EE"],["mcp","EDF2EE"],["compass","E3B341"],["file","EDF2EE"],["layers","EDF2EE"],["git","EDF2EE"],["info","EDF2EE"],["toggle","EDF2EE"],["slash","EDF2EE"],["shield","E3B341"],["aws","A9B8AE"],["server","EDF2EE"],["jenkins","E8735F"],["gitlab","FC6D26"],["github","FFFFFF"],["cucumber","8FD694"],["x","FFFFFF"],["code","FFFFFF"],["check","FFFFFF"],["commit","FFFFFF"],["bell","A9B8AE"],["heart","E5484D"]];
await Promise.all(NEED.map(([k, c]) => iconPng(k, c)));
fs.writeFileSync(process.argv[2] || "icons.json", JSON.stringify(I));
console.log(Object.keys(I).length, "icons");
})();
