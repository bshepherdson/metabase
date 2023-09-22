# Metabase - new :npm-module playground

## Approach

- Single `:app` shadow-cljs build.
- `:app` build uses `:target :npm-module` and plays in webpack's world.
    - But it outputs to `target/cljs{,_release}` rather than into `frontend/src/cljs{,_release}` as on `master`.
- webpack is configured to find `cljs/` imports in the `target/cljs{,_release}` directory, bundling these files in.
- No extra webpack magic for this one - it's treating the CLJS output like any `node_modules` library.

## Status

Initial load succeeds, the app works.

Simple REPL evals like `(+ 2 3)` or `(js/console.log "Hello REPL")` work, but evaling a namespace with nonempty
requires doesn't work. Hot reloading on changing a CLJS file also fails.

## To run

Three commands in separate terminals:

- `clojure -M:run` to start the backend
- `yarn build-hot:cljs` to run shadow-cljs for the `:dev` build
- `yarn build-hot:js` to run webpack

And access the app on http://localhost:3000

(Note that the CSP policies hard-code that the Shadow server is on 9630. We should change that to a non-default port
and hard-code that so there's no collisions, but I haven't done it yet.)

## REPLing

This works okay at a basic level, evaling some expressions from the REPL. (I use Conjure over nrepl, for the record.)
But if I try to eval a whole namespace (eg. a test) I get errors on each deftest when it tries to execute
```js
(function() {
    (function() {
        metabase.lib.equality_test.untyped_map_test = (function metabase$lib$equality_test$untyped_map_test() {
            return cljs.test.test_var.call(null, metabase.lib.equality_test.untyped_map_test.cljs$lang$var);
        }
        );
        return (new cljs.core.Var(function() {
            return metabase.lib.equality_test.untyped_map_test;
        }
        ,new cljs.core.Symbol("metabase.lib.equality-test","untyped-map-test","metabase.lib.equality-test/untyped-map-test",-1147623895,null),cljs.core.PersistentHashMap.fromArrays([new cljs.core.Keyword(null,"parallel","parallel",-1863607128), new cljs.core.Keyword(null,"ns","ns",441598760), new cljs.core.Keyword(null,"name","name",1843675177), new cljs.core.Keyword(null,"file","file",-1269645878), new cljs.core.Keyword(null,"end-column","end-column",1425389514), new cljs.core.Keyword(null,"source","source",-433931539), new cljs.core.Keyword(null,"column","column",2078222095), new cljs.core.Keyword(null,"line","line",212345235), new cljs.core.Keyword(null,"end-line","end-line",1837326455), new cljs.core.Keyword(null,"arglists","arglists",1661989754), new cljs.core.Keyword(null,"doc","doc",1913296891), new cljs.core.Keyword(null,"test","test",577538877)], [true, new cljs.core.Symbol(null,"metabase.lib.equality-test","metabase.lib.equality-test",1932862129,null), new cljs.core.Symbol(null,"untyped-map-test","untyped-map-test",1589349989,null), "metabase/lib/equality_test.cljs", 37, "^:parallel untyped-map-test", 1, 1, 1, cljs.core.List.EMPTY, null, (cljs.core.truth_(metabase.lib.equality_test.untyped_map_test) ? metabase.lib.equality_test.untyped_map_test.cljs$lang$test : null)])));
    }
    )();
    // ...
})();
```
failing with
```
TypeError: Cannot set properties of undefined (setting 'untyped_map_test')
    at eval (eval at shadow$cljs$devtools$client$browser$global_eval (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746968:8), <anonymous>:3:45)
    at eval (eval at shadow$cljs$devtools$client$browser$global_eval (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746968:8), <anonymous>:6:1351)
    at eval (eval at shadow$cljs$devtools$client$browser$global_eval (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746968:8), <anonymous>:176:3)
    at Object.shadow$cljs$devtools$client$browser$global_eval [as global_eval] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746968:8)
    at Object.shadow$cljs$devtools$client$shared$IHostSpecific$do_invoke$arity$3 (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746994:44)
    at Object.shadow$cljs$devtools$client$shared$do_invoke [as do_invoke] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:749470:14)
    at shadow$cljs$devtools$client$shared$handle_repl_invoke (app-main.hot.bundle.js?3deb9d62f4670b5eb490:749609:50)
    at Object.shadow$cljs$devtools$client$shared$interpret_action [as interpret_action] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:749688:175)
    at Object.shadow$cljs$devtools$client$shared$interpret_actions [as interpret_actions] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:749720:43)
    at app-main.hot.bundle.js?3deb9d62f4670b5eb490:749913:43
```

## An edit to play with for hot reloading

Here's a
[sample question](http://localhost:3000/question/notebook#eyJkYXRhc2V0X3F1ZXJ5Ijp7InF1ZXJ5Ijp7InNvdXJjZS10YWJsZSI6Miwiam9pbnMiOlt7ImFsaWFzIjoiUHJvZHVjdHMiLCJjb25kaXRpb24iOlsiPSIsWyJmaWVsZCIsOSxudWxsXSxbImZpZWxkIiwzLHsiam9pbi1hbGlhcyI6IlByb2R1Y3RzIn1dXSwic291cmNlLXRhYmxlIjoxfV0sImFnZ3JlZ2F0aW9uIjpbWyJzdW0iLFsiZmllbGQiLDEzLHsiYmFzZS10eXBlIjoidHlwZS9GbG9hdCJ9XV1dfSwidHlwZSI6InF1ZXJ5IiwiZGF0YWJhc2UiOjF9LCJkaXNwbGF5IjoidGFibGUiLCJkaXNwbGF5SXNMb2NrZWQiOnRydWUsInBhcmFtZXRlcnMiOltdLCJ2aXN1YWxpemF0aW9uX3NldHRpbmdzIjp7InRhYmxlLmNvbHVtbnMiOlt7Im5hbWUiOiJJRCIsImZpZWxkUmVmIjpbImZpZWxkIiwxMCxudWxsXSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiVVNFUl9JRCIsImZpZWxkUmVmIjpbImZpZWxkIiwxNixudWxsXSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiUFJPRFVDVF9JRCIsImZpZWxkUmVmIjpbImZpZWxkIiw5LG51bGxdLCJlbmFibGVkIjp0cnVlfSx7Im5hbWUiOiJTVUJUT1RBTCIsImZpZWxkUmVmIjpbImZpZWxkIiwxNCxudWxsXSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiVEFYIiwiZmllbGRSZWYiOlsiZmllbGQiLDE3LG51bGxdLCJlbmFibGVkIjp0cnVlfSx7Im5hbWUiOiJUT1RBTCIsImZpZWxkUmVmIjpbImZpZWxkIiwxMyxudWxsXSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiRElTQ09VTlQiLCJmaWVsZFJlZiI6WyJmaWVsZCIsMTEsbnVsbF0sImVuYWJsZWQiOnRydWV9LHsibmFtZSI6IkNSRUFURURfQVQiLCJmaWVsZFJlZiI6WyJmaWVsZCIsMTUseyJ0ZW1wb3JhbC11bml0IjoiZGVmYXVsdCJ9XSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiUVVBTlRJVFkiLCJmaWVsZFJlZiI6WyJmaWVsZCIsMTIsbnVsbF0sImVuYWJsZWQiOnRydWV9LHsibmFtZSI6IklEXzIiLCJmaWVsZFJlZiI6WyJmaWVsZCIsMyx7ImpvaW4tYWxpYXMiOiJQcm9kdWN0cyJ9XSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiRUFOIiwiZmllbGRSZWYiOlsiZmllbGQiLDcseyJqb2luLWFsaWFzIjoiUHJvZHVjdHMifV0sImVuYWJsZWQiOnRydWV9LHsibmFtZSI6IlRJVExFIiwiZmllbGRSZWYiOlsiZmllbGQiLDIseyJqb2luLWFsaWFzIjoiUHJvZHVjdHMifV0sImVuYWJsZWQiOnRydWV9LHsibmFtZSI6IkNBVEVHT1JZIiwiZmllbGRSZWYiOlsiZmllbGQiLDUseyJqb2luLWFsaWFzIjoiUHJvZHVjdHMifV0sImVuYWJsZWQiOnRydWV9LHsibmFtZSI6IlZFTkRPUiIsImZpZWxkUmVmIjpbImZpZWxkIiw0LHsiam9pbi1hbGlhcyI6IlByb2R1Y3RzIn1dLCJlbmFibGVkIjp0cnVlfSx7Im5hbWUiOiJQUklDRSIsImZpZWxkUmVmIjpbImZpZWxkIiwxLHsiam9pbi1hbGlhcyI6IlByb2R1Y3RzIn1dLCJlbmFibGVkIjp0cnVlfSx7Im5hbWUiOiJSQVRJTkciLCJmaWVsZFJlZiI6WyJmaWVsZCIsOCx7ImpvaW4tYWxpYXMiOiJQcm9kdWN0cyJ9XSwiZW5hYmxlZCI6dHJ1ZX0seyJuYW1lIjoiQ1JFQVRFRF9BVF8yIiwiZmllbGRSZWYiOlsiZmllbGQiLDYseyJ0ZW1wb3JhbC11bml0IjoiZGVmYXVsdCIsImpvaW4tYWxpYXMiOiJQcm9kdWN0cyJ9XSwiZW5hYmxlZCI6dHJ1ZX1dLCJ0YWJsZS5waXZvdF9jb2x1bW4iOiJDQVRFR09SWSIsInRhYmxlLmNlbGxfY29sdW1uIjoiU1VCVE9UQUwifSwib3JpZ2luYWxfY2FyZF9pZCI6OH0=)
to try. Note the `Sum of !!Total` on the green aggregation. That's coming from `src/metabase/lib/field.cljc` line 225.
Try editing that `"!!"` to `"11"` or something and save the file.

That fails with
```
Failed to load metabase/lib/field.cljc TypeError: goog.provide is not a function
    at eval (metabase.lib.field.js:1:6)
    at eval (<anonymous>)
    at goog.globalEval (app-main.hot.bundle.js?3deb9d62f4670b5eb490:634947:11)
    at Object.shadow$cljs$devtools$client$browser$script_eval [as script_eval] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:745963:13)
    at Object.shadow$cljs$devtools$client$browser$do_js_load [as do_js_load] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746033:41)
    at app-main.hot.bundle.js?3deb9d62f4670b5eb490:746068:44
    at app-main.hot.bundle.js?3deb9d62f4670b5eb490:748337:107
    at Object.shadow$cljs$devtools$client$env$do_js_reload_STAR_ [as do_js_reload_STAR_] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:748284:98)
    at Function.cljs$core$IFn$_invoke$arity$4 (app-main.hot.bundle.js?3deb9d62f4670b5eb490:748347:40)
    at Object.shadow$cljs$devtools$client$browser$do_js_reload [as do_js_reload] (app-main.hot.bundle.js?3deb9d62f4670b5eb490:746061:53)
```
which feels like the hot reloading is producing the wrong flavour of JS, ie. `:browser` style with
`goog.provide`/`goog.require` rather than webpack-friendly `:npm-module` style.

(Note that even if that code reloads properly, no one is telling React to re-render, so the UI won't actually update.
If hot reloading were working, you could test it by removing the `Sum of !!Total` and adding it back, for example.)
