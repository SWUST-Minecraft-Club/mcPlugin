#include <jni.h>
#include <RmlUi/Core.h>
#include <RmlUi/Core/Elements/ElementFormControl.h>
#include <algorithm>
#include <chrono>
#include <codecvt>
#include <locale>
#include <map>
#include <memory>
#include <sstream>
#include <vector>

namespace {
using namespace Rml;
std::string string(JNIEnv* env, jstring value) {
    if (!value) return {};
    auto data = env->GetStringChars(value, nullptr);
    std::u16string utf16(reinterpret_cast<const char16_t*>(data), env->GetStringLength(value));
    env->ReleaseStringChars(value, data);
    return std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().to_bytes(utf16);
}
jstring javaString(JNIEnv* env, const std::string& value) {
    auto utf16 = std::wstring_convert<std::codecvt_utf8_utf16<char16_t>, char16_t>().from_bytes(value);
    return env->NewString(reinterpret_cast<const jchar*>(utf16.data()), static_cast<jsize>(utf16.size()));
}
std::string quote(const std::string& text) {
    std::string result = "\"";
    for (unsigned char c : text) {
        if (c == '"' || c == '\\') { result += '\\'; result += c; }
        else if (c < 32) { const char* hex = "0123456789abcdef"; result += "\\u00"; result += hex[c >> 4]; result += hex[c & 15]; }
        else result += c;
    }
    return result + '"';
}
String accessibleText(Element* element) {
    if(auto text=dynamic_cast<ElementText*>(element))return text->GetText();
    String result;
    for(int i=0;i<element->GetNumChildren();i++)result+=accessibleText(element->GetChild(i));
    return result;
}
std::string escape(const std::string& text) {
    std::string out;
    for (char c : text) {
        if (c == '&') out += "&amp;";
        else if (c == '<') out += "&lt;";
        else if (c == '>') out += "&gt;";
        else out += c;
    }
    return out;
}
struct DenyFiles : FileInterface {
    FileHandle Open(const String&) override { return 0; }
    void Close(FileHandle) override {}
    size_t Read(void*, size_t, FileHandle) override { return 0; }
    bool Seek(FileHandle, long, int) override { return false; }
    size_t Tell(FileHandle) override { return 0; }
} files;
struct Host;
Host* active = nullptr;
struct System : SystemInterface {
    double GetElapsedTime() override {
        return std::chrono::duration<double>(std::chrono::steady_clock::now().time_since_epoch()).count();
    }
    bool LogMessage(Log::Type type, const String& text) override;
    void SetClipboardText(const String& text) override;
    void GetClipboardText(String& text) override;
} systemInterface;
struct Pixels { std::vector<byte> bytes; int width, height; };
struct Renderer : RenderInterface {
    Host* owner;
    uintptr_t next = 1;
    bool scissor = false;
    Rectanglei clip;
    const Matrix4f* transform = nullptr;
    std::map<std::string, Pixels> images;
    explicit Renderer(Host* owner) : owner(owner) {}
    CompiledGeometryHandle CompileGeometry(Span<const Vertex> vertices, Span<const int> indices) override;
    void RenderGeometry(CompiledGeometryHandle geometry, Vector2f translation, TextureHandle texture) override;
    void ReleaseGeometry(CompiledGeometryHandle geometry) override;
    TextureHandle LoadTexture(Vector2i& dimensions, const String& source) override;
    TextureHandle GenerateTexture(Span<const byte> bytes, Vector2i dimensions) override;
    void ReleaseTexture(TextureHandle texture) override;
    void EnableScissorRegion(bool enable) override { scissor = enable; }
    void SetScissorRegion(Rectanglei value) override { clip = value; }
    void SetTransform(const Matrix4f* value) override { transform = value; }
};
struct Listener : EventListener {
    Host* owner;
    explicit Listener(Host* owner) : owner(owner) {}
    void ProcessEvent(Event& event) override;
};
struct Host {
    JNIEnv* env;
    jobject callback;
    jmethodID geometry, draw, texture, release, event, log, item, terrain;
    std::unique_ptr<Renderer> renderer;
    std::unique_ptr<Listener> listener;
    Context* context = nullptr;
    ElementDocument* document = nullptr;
    std::vector<std::vector<byte>> fonts;
    std::string clipboard;
    String modalReturnFocus;
    Host(JNIEnv* env, jobject callback) : env(env), callback(env->NewGlobalRef(callback)) {
        auto klass = env->GetObjectClass(callback);
        item = env->GetMethodID(klass, "item", "(Ljava/lang/String;FFFFIIII)V");
        terrain = env->GetMethodID(klass, "terrain", "(Ljava/lang/String;IIIFFFFIIII)V");
        geometry = env->GetMethodID(klass, "geometry", "(J[F[I[I)V");
        draw = env->GetMethodID(klass, "draw", "(JJFFIIII[F)V");
        texture = env->GetMethodID(klass, "texture", "(J[BII)V");
        release = env->GetMethodID(klass, "release", "(JZ)V");
        event = env->GetMethodID(klass, "event", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");
        log = env->GetMethodID(klass, "log", "(Ljava/lang/String;)V");
        env->DeleteLocalRef(klass);
        renderer = std::make_unique<Renderer>(this);
        listener = std::make_unique<Listener>(this);
    }
    void emit(const std::string& action, const std::string& id, const std::string& value) {
        auto a = javaString(env, action), b = javaString(env, id), c = javaString(env, value);
        env->CallVoidMethod(callback, event, a, b, c);
        env->DeleteLocalRef(a); env->DeleteLocalRef(b); env->DeleteLocalRef(c);
    }
};
class ItemSlot : public Element {
public:
    explicit ItemSlot(const String& tag) : Element(tag) {}
protected:
    void OnRender() override {
        if(!active)return;
        auto h=active;auto point=GetAbsoluteOffset(BoxArea::Border);auto size=GetBox().GetSize(BoxArea::Border);
        auto& renderer=*h->renderer;auto id=javaString(h->env,GetAttribute<String>("item", ""));
        h->env->CallVoidMethod(h->callback,h->item,id,point.x,point.y,size.x,size.y,
            renderer.scissor?renderer.clip.Left():0,renderer.scissor?renderer.clip.Top():0,
            renderer.scissor?renderer.clip.Width():-1,renderer.scissor?renderer.clip.Height():-1);
        h->env->DeleteLocalRef(id);
    }
};
ElementInstancerGeneric<ItemSlot> itemSlots;
class TerrainMap : public Element {
public:
    explicit TerrainMap(const String& tag) : Element(tag) {}
protected:
    void OnRender() override {
        if(!active)return;
        auto h=active;auto point=GetAbsoluteOffset(BoxArea::Border);auto size=GetBox().GetSize(BoxArea::Border);
        auto& renderer=*h->renderer;auto dimension=javaString(h->env,GetAttribute<String>("dimension",""));
        h->env->CallVoidMethod(h->callback,h->terrain,dimension,GetAttribute<int>("chunk-x",0),GetAttribute<int>("chunk-z",0),GetAttribute<int>("chunks",7),point.x,point.y,size.x,size.y,
            renderer.scissor?renderer.clip.Left():0,renderer.scissor?renderer.clip.Top():0,
            renderer.scissor?renderer.clip.Width():-1,renderer.scissor?renderer.clip.Height():-1);
        h->env->DeleteLocalRef(dimension);
    }
};
ElementInstancerGeneric<TerrainMap> terrainMaps;
bool System::LogMessage(Log::Type type, const String& text) {
    if (active && type <= Log::LT_WARNING) {
        auto s = javaString(active->env, text);
        active->env->CallVoidMethod(active->callback, active->log, s);
        active->env->DeleteLocalRef(s);
    }
    return true;
}
void System::SetClipboardText(const String& text) { if (active) { active->clipboard = text; active->emit("clipboard:set", "", text); } }
void System::GetClipboardText(String& text) { if (active) text = active->clipboard; }
CompiledGeometryHandle Renderer::CompileGeometry(Span<const Vertex> vertices, Span<const int> indices) {
    auto env = owner->env;
    std::vector<float> data(vertices.size() * 4);
    std::vector<jint> colors(vertices.size());
    for (size_t i = 0; i < vertices.size(); i++) {
        auto v = vertices[i];
        data[i*4] = v.position.x; data[i*4+1] = v.position.y;
        data[i*4+2] = v.tex_coord.x; data[i*4+3] = v.tex_coord.y;
        colors[i] = (uint32_t(v.colour.alpha) << 24) | (uint32_t(v.colour.red) << 16) | (uint32_t(v.colour.green) << 8) | v.colour.blue;
    }
    auto points = env->NewFloatArray(static_cast<jsize>(data.size()));
    auto rgba = env->NewIntArray(static_cast<jsize>(colors.size()));
    auto order = env->NewIntArray(static_cast<jsize>(indices.size()));
    env->SetFloatArrayRegion(points, 0, static_cast<jsize>(data.size()), data.data());
    env->SetIntArrayRegion(rgba, 0, static_cast<jsize>(colors.size()), colors.data());
    env->SetIntArrayRegion(order, 0, static_cast<jsize>(indices.size()), indices.data());
    auto id = next++;
    env->CallVoidMethod(owner->callback, owner->geometry, jlong(id), points, rgba, order);
    env->DeleteLocalRef(points); env->DeleteLocalRef(rgba); env->DeleteLocalRef(order);
    return id;
}
void Renderer::RenderGeometry(CompiledGeometryHandle geometry, Vector2f translation, TextureHandle texture) {
    auto env = owner->env;
    jfloatArray matrix = nullptr;
    if (transform) {
        matrix = env->NewFloatArray(16);
        env->SetFloatArrayRegion(matrix, 0, 16, transform->data());
    }
    env->CallVoidMethod(owner->callback, owner->draw, jlong(geometry), jlong(texture), translation.x, translation.y,
        scissor ? clip.Left() : 0, scissor ? clip.Top() : 0, scissor ? clip.Width() : -1, scissor ? clip.Height() : -1, matrix);
    if (matrix) env->DeleteLocalRef(matrix);
}
void Renderer::ReleaseGeometry(CompiledGeometryHandle id) { owner->env->CallVoidMethod(owner->callback, owner->release, jlong(id), jboolean(false)); }
TextureHandle Renderer::GenerateTexture(Span<const byte> source, Vector2i size) {
    auto env = owner->env;
    auto bytes = env->NewByteArray(static_cast<jsize>(source.size()));
    env->SetByteArrayRegion(bytes, 0, static_cast<jsize>(source.size()), reinterpret_cast<const jbyte*>(source.data()));
    auto id = next++;
    env->CallVoidMethod(owner->callback, owner->texture, jlong(id), bytes, size.x, size.y);
    env->DeleteLocalRef(bytes);
    return id;
}
TextureHandle Renderer::LoadTexture(Vector2i& size, const String& source) {
    std::string key = source;
    for (auto scheme : {"asset:", "builtin:"}) { auto index = key.find(scheme); if (index != std::string::npos) { key = key.substr(index); break; } }
    auto image = images.find(key);
    if (image == images.end()) return 0;
    auto& pixels = image->second;
    size = {pixels.width, pixels.height};
    return GenerateTexture(Span<const byte>(pixels.bytes.data(), pixels.bytes.size()), size);
}
void Renderer::ReleaseTexture(TextureHandle id) { owner->env->CallVoidMethod(owner->callback, owner->release, jlong(id), jboolean(true)); }
void updateValueLabel(Element* element, ElementDocument* document) {
    auto input=dynamic_cast<ElementFormControl*>(element);if(!input)return;
    auto id=element->GetAttribute<String>("value-target","");if(id.empty())return;
    auto output=document->GetElementById(id);if(!output)return;
    if(output->GetTagName()!="span"&&output->GetTagName()!="p")return;
    for(int i=0;i<output->GetNumChildren();++i)if(output->GetChild(i)->GetTagName()!="#text")return;
    // A label must never replace the input itself or an ancestor owning the active event target.
    for(auto ancestor=element;ancestor;ancestor=ancestor->GetParentNode())if(ancestor==output)return;
    auto value=input->GetValue();
    if(element->GetAttribute<String>("type","")=="range"&&value.find('.')!=String::npos){
        while(!value.empty()&&value.back()=='0')value.pop_back();
        if(!value.empty()&&value.back()=='.')value.pop_back();
    }
    output->SetInnerRML(escape(value));
}
void Listener::ProcessEvent(Event& event) {
    auto target = event.GetTargetElement();
    if (event.GetType() == "change") {
        if (auto input = dynamic_cast<ElementFormControl*>(target)) {
            updateValueLabel(target,owner->document);
            owner->emit("change", target->GetId(), input->GetValue());
        }
        return;
    }
    while (target && target != owner->document && !target->HasAttribute("action")) target = target->GetParentNode();
    if (!target || !target->HasAttribute("action")) return;
    auto action = target->GetAttribute<String>("action", "");
    if (action.rfind("tab:", 0) == 0) {
        ElementList pages; owner->document->QuerySelectorAll(pages, ".tab-page");
        for (auto page : pages) page->SetClass("hidden", page->GetId() != action.substr(4));
        ElementList tabs; owner->document->QuerySelectorAll(tabs, ".mc-tab");
        for (auto tab : tabs) tab->SetClass("selected", tab == target);
    } else if (action.rfind("filter:", 0) == 0) {
        auto category=action.substr(7);
        ElementList entries; owner->document->QuerySelectorAll(entries,"[filter-category]");
        for(auto entry:entries) entry->SetClass("hidden",category!="all"&&entry->GetAttribute<String>("filter-category","")!=category);
        ElementList filters; owner->document->QuerySelectorAll(filters,".filter-button");
        for(auto filter:filters) filter->SetClass("selected",filter==target);
    } else if (action.rfind("toggle:", 0) == 0) {
        if (auto element = owner->document->GetElementById(action.substr(7))) {
            bool opening=element->IsClassSet("hidden");element->SetClass("hidden",!opening);
            if(element->IsClassSet("mc-modal")) {
                if(opening){owner->modalReturnFocus=target->GetId();owner->context->Update();if(auto first=element->QuerySelector("button, input, textarea, select"))first->Focus();}
                else if(auto previous=owner->document->GetElementById(owner->modalReturnFocus))previous->Focus();
            }
        }
    } else if (action.rfind("count:", 0) == 0) {
        if (auto element = owner->document->GetElementById(action.substr(6))) {
            int count = element->GetAttribute<int>("count", 0) + 1;
            element->SetAttribute("count", count); element->SetInnerRML(std::to_string(count));
        }
    }
    owner->emit(action, target->GetId(), "");
}
Host* host(JNIEnv* env, jlong handle) { auto h = reinterpret_cast<Host*>(handle); h->env = env; active = h; return h; }
Input::KeyIdentifier keyCode(int code) {
    using namespace Input;
    if (code >= 4 && code <= 29) return static_cast<KeyIdentifier>(KI_A + code - 4);
    if (code >= 30 && code <= 38) return static_cast<KeyIdentifier>(KI_1 + code - 30);
    switch (code) {
        case 39: return KI_0; case 40: return KI_RETURN; case 41: return KI_ESCAPE; case 42: return KI_BACK;
        case 43: return KI_TAB; case 44: return KI_SPACE; case 74: return KI_HOME; case 75: return KI_PRIOR;
        case 76: return KI_DELETE; case 77: return KI_END; case 78: return KI_NEXT; case 79: return KI_RIGHT;
        case 80: return KI_LEFT; case 81: return KI_DOWN; case 82: return KI_UP;
        case 224: return KI_LCONTROL; case 225: return KI_LSHIFT; case 226: return KI_LMENU; case 227: return KI_LWIN;
        default: return KI_UNKNOWN;
    }
}
int mods(int value) { return ((value & 1) ? Input::KM_SHIFT : 0) | ((value & 2) ? Input::KM_CTRL : 0) | ((value & 4) ? Input::KM_ALT : 0) | ((value & 8) ? Input::KM_META : 0); }
}

extern "C" {
JNIEXPORT jlong JNICALL Java_com_example_yudreamui_runtime_NativeEngine_create(JNIEnv* env, jobject object) {
    if (active) { env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Only one UI engine owner is allowed"); return 0; }
    auto h = new Host(env, object); active = h;
    SetSystemInterface(&systemInterface); SetFileInterface(&files); SetRenderInterface(h->renderer.get());
    if (!Initialise()) { env->DeleteGlobalRef(h->callback); delete h; active = nullptr; return 0; }
    Factory::RegisterElementInstancer("mcslot", &itemSlots);
    Factory::RegisterElementInstancer("mcterrain", &terrainMaps);
    h->context = CreateContext("yudream-ui", {800, 450});
    return reinterpret_cast<jlong>(h);
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_destroy(JNIEnv* env, jobject, jlong value) {
    auto h = host(env, value); Shutdown(); env->DeleteGlobalRef(h->callback); delete h; active = nullptr;
}
JNIEXPORT jboolean JNICALL Java_com_example_yudreamui_runtime_NativeEngine_font(JNIEnv* env, jobject, jlong value, jbyteArray data, jstring family) {
    auto h = host(env, value);
    h->fonts.emplace_back(env->GetArrayLength(data)); auto& bytes = h->fonts.back();
    env->GetByteArrayRegion(data, 0, static_cast<jsize>(bytes.size()), reinterpret_cast<jbyte*>(bytes.data()));
    bool loaded=LoadFontFace(Span<const byte>(bytes.data(), bytes.size()), string(env, family), Style::FontStyle::Normal, Style::FontWeight::Normal, true);
    if(!loaded)h->fonts.pop_back();return loaded;
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_open(JNIEnv* env, jobject, jlong value, jstring source, jint width, jint height) {
    auto h = host(env, value);
    struct Saved { String value; float x, y; bool focus, checked, page, hidden, tab, selected; int count; };
    std::map<std::string, Saved> saved;
    if (h->document) {
        ElementList elements; h->document->QuerySelectorAll(elements, "[id]");
        for (auto element : elements) {
            auto control = dynamic_cast<ElementFormControl*>(element);
            saved[element->GetId()] = {control ? control->GetValue() : "", element->GetScrollLeft(), element->GetScrollTop(), element == h->context->GetFocusElement(), element->HasAttribute("checked"), element->IsClassSet("tab-page")||element->HasAttribute("filter-category"), element->IsClassSet("hidden"), element->IsClassSet("mc-tab"), element->IsClassSet("selected"), element->GetAttribute<int>("count", -1)};
        }
    }
    h->context->SetDimensions({width, height});
    auto next = h->context->LoadDocumentFromMemory(string(env, source), "ui://screen.rml");
    if (!next) { env->ThrowNew(env->FindClass("java/lang/IllegalArgumentException"), "RmlUi rejected UI document"); return; }
    auto previous = h->document; h->document = next;
    next->AddEventListener("click", h->listener.get()); next->AddEventListener("change", h->listener.get());
    next->Show(); h->context->Update();
    for (auto& pair : saved) if (auto element = next->GetElementById(pair.first)) {
        if (auto control = dynamic_cast<ElementFormControl*>(element)) control->SetValue(pair.second.value);
        if (pair.second.checked) element->SetAttribute("checked", ""); else element->RemoveAttribute("checked");
        if (pair.second.page) element->SetClass("hidden", pair.second.hidden);
        if (pair.second.tab) element->SetClass("selected", pair.second.selected);
        if (pair.second.count >= 0) { element->SetAttribute("count", pair.second.count); element->SetInnerRML(std::to_string(pair.second.count)); }
    }
    if (previous) previous->Close();
    ElementList outputs; next->QuerySelectorAll(outputs,"[value-target]");
    for(auto element:outputs)updateValueLabel(element,next);
    h->context->Update();
    // Restore offsets only after hidden pages have their final layout and scroll extent.
    for (auto& pair : saved) if (auto element = next->GetElementById(pair.first)) {
        element->SetScrollLeft(pair.second.x); element->SetScrollTop(pair.second.y);
        if (pair.second.focus) element->Focus();
    }
    h->context->Update();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_hide(JNIEnv* env, jobject, jlong value) {
    auto h = host(env, value); if (h->document) { h->document->Close(); h->document = nullptr; h->context->Update(); }
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_frame(JNIEnv* env, jobject, jlong value, jint width, jint height, jint x, jint y) {
    auto h = host(env, value); h->context->SetDimensions({width, height}); h->context->ProcessMouseMove(x, y, 0);
    h->context->Update(); h->context->Render();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_image(JNIEnv* env, jobject, jlong value, jstring name, jbyteArray data, jint width, jint height) {
    auto h = host(env, value); Pixels pixels; pixels.width = width; pixels.height = height; pixels.bytes.resize(env->GetArrayLength(data));
    env->GetByteArrayRegion(data, 0, static_cast<jsize>(pixels.bytes.size()), reinterpret_cast<jbyte*>(pixels.bytes.data()));
    h->renderer->images[string(env, name)] = std::move(pixels);
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_text(JNIEnv* env, jobject, jlong value, jstring binding, jstring text) {
    auto h = host(env, value); if (!h->document) return;
    ElementList elements; h->document->QuerySelectorAll(elements, "[bind]");
    auto name = string(env, binding), data = string(env, text);
    for (auto element : elements) if (element->GetAttribute<String>("bind", "") == name) {
        if (element->GetTagName() == "progress") element->SetAttribute("value", data);
        else element->SetInnerRML(escape(data));
    }
    h->context->Update();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_mouse(JNIEnv* env, jobject, jlong value, jint type, jfloat x, jfloat y, jint button, jint modifiers) {
    auto h = host(env, value); h->context->ProcessMouseMove(static_cast<int>(x), static_cast<int>(y), mods(modifiers));
    if (type == 1) h->context->ProcessMouseButtonDown(button, mods(modifiers));
    if (type == 2) h->context->ProcessMouseButtonUp(button, mods(modifiers));
    h->context->Update();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_scroll(JNIEnv* env, jobject, jlong value, jfloat delta) {
    auto h = host(env, value); h->context->ProcessMouseWheel(-delta * 35, 0); h->context->Update();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_key(JNIEnv* env, jobject, jlong value, jint key, jint modifiers, jboolean down) {
    auto h = host(env, value);
    Element* modal=nullptr;
    if(h->document){ElementList candidates;h->document->QuerySelectorAll(candidates,".mc-modal");for(auto candidate:candidates)if(candidate->IsVisible(true))modal=candidate;}
    if(down&&key==41) {
        if(modal){modal->SetClass("hidden",true);if(auto previous=h->document->GetElementById(h->modalReturnFocus))previous->Focus();}
        else h->emit("ui:close","","");
        h->context->Update();return;
    }
    if(down&&key==43&&modal) {
        ElementList controls;modal->QuerySelectorAll(controls,"button, input, textarea, select");
        controls.erase(std::remove_if(controls.begin(),controls.end(),[](Element* e){return !e->IsVisible(true)||e->HasAttribute("disabled");}),controls.end());
        if(!controls.empty()) {
            auto found=std::find(controls.begin(),controls.end(),h->context->GetFocusElement());
            int index=found==controls.end()?(modifiers&1?0:-1):int(found-controls.begin());
            controls[(index+(modifiers&1?-1:1)+int(controls.size()))%controls.size()]->Focus();
        }
        h->context->Update();return;
    }
    if (down) h->context->ProcessKeyDown(keyCode(key), mods(modifiers)); else h->context->ProcessKeyUp(keyCode(key), mods(modifiers)); h->context->Update();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_type(JNIEnv* env, jobject, jlong value, jstring text) {
    auto h = host(env, value); h->context->ProcessTextInput(string(env, text)); h->context->Update();
}
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_clipboard(JNIEnv* env, jobject, jlong value, jstring text) { host(env, value)->clipboard = string(env, text); }
JNIEXPORT void JNICALL Java_com_example_yudreamui_runtime_NativeEngine_pruneImages(JNIEnv* env, jobject, jlong value) {
    auto h=host(env,value); std::map<std::string, Pixels> live;
    auto sources=GetTextureSourceList();
    // New documents retain only their image bytes; obsolete GPU textures are retired through the host fence queue.
    if(h->document) {
        ElementList elements; h->document->QuerySelectorAll(elements,"img");
        for(auto element:elements) {auto source=element->GetAttribute<String>("src", "");auto found=h->renderer->images.find(source);if(found!=h->renderer->images.end())live.emplace(source,found->second);}
    }
    for(auto& pair:h->renderer->images) if(!live.count(pair.first)) {
        for(auto& source:sources) if(source.find(pair.first)!=String::npos) ReleaseTexture(source,h->renderer.get());
    }
    h->renderer->images=std::move(live);
}
JNIEXPORT jstring JNICALL Java_com_example_yudreamui_runtime_NativeEngine_inspect(JNIEnv* env, jobject, jlong value) {
    auto h = host(env, value); std::ostringstream out; out << '['; bool first = true;
    if (h->document) {
        ElementList elements; h->document->QuerySelectorAll(elements, "[id]");
        for (auto element : elements) {
            auto point = element->GetAbsoluteOffset(BoxArea::Border); auto size = element->GetBox().GetSize(BoxArea::Border);
            if (!first) out << ','; first = false;
            auto input = dynamic_cast<ElementFormControl*>(element);
            bool textOnly=true;
            for(int i=0;i<element->GetNumChildren();i++)if(element->GetChild(i)->GetTagName()!="#text")textOnly=false;
            float cx=0,cy=0,cr=static_cast<float>(h->context->GetDimensions().x),cb=static_cast<float>(h->context->GetDimensions().y);
            for(auto parent=element->GetParentNode();parent;parent=parent->GetParentNode()) {
                auto p=parent->GetAbsoluteOffset(BoxArea::Padding);auto b=parent->GetBox().GetSize(BoxArea::Padding);
                if(parent->GetComputedValues().overflow_x()!=Style::Overflow::Visible){cx=std::max(cx,p.x);cr=std::min(cr,p.x+b.x);}
                if(parent->GetComputedValues().overflow_y()!=Style::Overflow::Visible){cy=std::max(cy,p.y);cb=std::min(cb,p.y+b.y);}
            }
            float vx=std::max(cx,point.x),vy=std::max(cy,point.y),vr=std::min(cr,point.x+size.x),vb=std::min(cb,point.y+size.y);
            out << "{\"id\":" << quote(element->GetId()) << ",\"tag\":" << quote(element->GetTagName())
                << ",\"label\":" << quote(element->GetAttribute<String>("aria-label", input ? element->GetId() : accessibleText(element)))
                << ",\"value\":" << quote(input ? input->GetValue() : textOnly ? element->GetInnerRML() : "")
                << ",\"type\":" << quote(element->GetAttribute<String>("type", ""))
                << ",\"action\":" << quote(element->GetAttribute<String>("action", ""))
                << ",\"checked\":" << (element->HasAttribute("checked") ? "true" : "false")
                << ",\"disabled\":" << (element->HasAttribute("disabled") ? "true" : "false")
                << ",\"item\":" << quote(element->GetAttribute<String>("item", ""))
                << ",\"dimension\":" << quote(element->GetAttribute<String>("dimension", ""))
                << ",\"chunkX\":" << element->GetAttribute<int>("chunk-x",0) << ",\"chunkZ\":" << element->GetAttribute<int>("chunk-z",0)
                << ",\"chunks\":" << element->GetAttribute<int>("chunks",0)
                << ",\"visible\":" << (element->IsVisible(true) && vr>vx && vb>vy ? "true" : "false")
                << ",\"focused\":" << (h->context->GetFocusElement() == element ? "true" : "false")
                << ",\"x\":" << point.x << ",\"y\":" << point.y << ",\"width\":" << size.x << ",\"height\":" << size.y
                << ",\"clipX\":" << vx << ",\"clipY\":" << vy << ",\"clipWidth\":" << std::max(0.f,vr-vx) << ",\"clipHeight\":" << std::max(0.f,vb-vy)
                << ",\"scrollTop\":" << element->GetScrollTop() << '}';
        }
    }
    out << ']'; return javaString(env, out.str());
}
}
