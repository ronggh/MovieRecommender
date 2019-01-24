package cn.alan.server.restapi;

import cn.alan.server.model.core.User;
import cn.alan.server.model.request.UpdateUserGenresRequest;
import cn.alan.server.model.request.UserLoginRequest;
import cn.alan.server.model.request.UserRegisterRequest;
import cn.alan.server.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/rest/users")
@Controller
public class UserRestApi {
    //
    @Autowired
    private UserService userService;

    /**
     * 用户注册
     * 请求的url类似:/rest/users/register?username=aaaa&password=123456
     *
     * @param userName
     * @param password
     * @param model
     * @return {"success":true|false}
     */
    @RequestMapping(value = "/register", produces = "application/json", method = RequestMethod.GET)
    @ResponseBody
    public Model registerUser(@RequestParam("username") String userName, @RequestParam("password") String password, Model model) {
        // 构造请求
        UserRegisterRequest request = new UserRegisterRequest(userName, password);
        // 注册
        model.addAttribute("success", userService.userRegister(request));

        return model;
    }

    /**
     * 请求的url类似:/rest/users/login?username=abc&password=123456
     *
     * @param userName
     * @param password
     * @param model
     * @return{"success":true|false}
     */
    @RequestMapping(value = "/login", produces = "application/json", method = RequestMethod.GET)
    @ResponseBody
    public Model loginUser(@RequestParam("username") String userName, @RequestParam("password") String password, Model model) {
        // 构造请求
        UserLoginRequest request = new UserLoginRequest(userName, password);
        model.addAttribute("success", userService.userLogin(request));
        return model;
    }

    /**
     * 需要能够添加用户偏爱的影片类别
     * 访问：url: /rest/users/genres?username=abc&genres=a|b|c|d
     *
     * @param username
     * @param genres
     * @param model
     */
    @RequestMapping(path = "/genres", produces = "application/json", method = RequestMethod.GET)
    @ResponseBody
    public void addGenres(@RequestParam("username") String username, @RequestParam("genres") String genres, Model model) {
        List<String> genresList = new ArrayList<>();
        for (String gen : genres.split("\\|"))
            genresList.add(gen);
        userService.updateUserGenres(new UpdateUserGenresRequest(username, genresList));
    }
}
