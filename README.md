# Metabase - hacked up for :browser build

## Approach

- Separate `:dev` and `:app` (prod) shadow-cljs buids.
- `:dev` uses `:target :browser` and outputs to `target/cljs`
- webpack is configured to treat `cljs/` imports as "var" externals
    - So `import * as Lib from "cljs/metabase.lib.js";` becomes effectively `const Lib = metabase.lib.js;` using globals.
- `frontend/src/metabase/dev.js` requires `import "cljs_dev/main";`
    - This is another external in webpack, with type "script".
    - That (async) adds a `<script>` tag for `main.js`.

## Status

Fails to load in the browser. There's an incompatible timing problem here.

- The `:js-provider :external` webpack bundled file must be loaded first, so that `shadow$bridge` is defined before the
  `main.js` file runs.
    - Therefore `<head><script src="/app/dist/main.js"></script></head>` won't work. (ie. loading the CLJS output outside webpack)
- Webpack "script" externals get loaded async, but the webpacked code continues loading. So it tries to access
  `window.metabase.foo` before the CLJS code loads and defines those globals.

## To run

Three commands in separate terminals:

- `clojure -M:run` to start the backend
- `yarn build-hot:cljs` to run shadow-cljs for the `:dev` build
- `yarn build-hot:js` to run webpack

And access the app on http://localhost:3000
