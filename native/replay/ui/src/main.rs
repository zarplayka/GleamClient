#![windows_subsystem = "windows"]

use std::{env, error::Error};
use tao::{
    dpi::{PhysicalPosition, PhysicalSize},
    event::{Event, WindowEvent},
    event_loop::{ControlFlow, EventLoopBuilder},
    platform::windows::WindowBuilderExtWindows,
    window::WindowBuilder,
};
use wry::{NewWindowResponse, WebViewBuilder, WebViewBuilderExtWindows};

fn main() -> Result<(), Box<dyn Error>> {
    let args: Vec<_> = env::args().skip(1).collect();
    let [url, owner, x, y, width, height] = args.as_slice() else {
        return Err("Missing replay window arguments".into());
    };
    if !url.starts_with("http://127.0.0.1:") || url.contains(['@', '#']) {
        return Err("Invalid replay window URL".into());
    }

    let event_loop = EventLoopBuilder::<()>::with_user_event().build();
    let close = event_loop.create_proxy();
    let window = WindowBuilder::new()
        .with_title("GleamClient · Clips")
        .with_owner_window(owner.parse()?)
        .with_position(PhysicalPosition::new(x.parse::<i32>()?, y.parse::<i32>()?))
        .with_inner_size(PhysicalSize::new(width.parse::<u32>()?, height.parse::<u32>()?))
        .with_decorations(false)
        .with_visible(false)
        .with_skip_taskbar(true)
        .build(&event_loop)?;

    let allowed = url.clone();
    let webview = WebViewBuilder::new()
        .with_url(url)
        .with_background_color((20, 21, 28, 255))
        .with_browser_accelerator_keys(false)
        .with_incognito(true)
        .with_navigation_handler(move |url| url == allowed)
        .with_new_window_req_handler(|_, _| NewWindowResponse::Deny)
        .with_ipc_handler(move |message| {
            if message.body() == "close" {
                let _ = close.send_event(());
            }
        })
        .build(&window)?;
    window.set_visible(true);
    window.set_focus();
    event_loop.run(move |event, _, flow| {
        let _keep_alive = (&webview, &window);
        *flow = ControlFlow::Wait;
        if matches!(event, Event::UserEvent(()) | Event::WindowEvent { event: WindowEvent::CloseRequested, .. }) {
            *flow = ControlFlow::Exit;
        }
    });
}
