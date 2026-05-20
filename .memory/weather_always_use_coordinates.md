---
name: weather_always_use_coordinates
description: 查询天气一律使用经纬度坐标，不要用城市名查询
type: feedback
---
用户要求：查询任何地方的天气都直接用经纬度坐标调用 get_weather 或 get_forecast，不要使用城市名（中文或英文）先搜索再查。如果不知道某地的坐标，可以用 search_location 获取坐标后，后续直接用坐标查。
