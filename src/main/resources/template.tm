<html>
<body>
  <span t:text="#{welcome.message}" />
  <table>
    <tr t:each="student: ${students}">
      <td t:text="${student.id}" />
      <td t:text="${student.name}" />
    </tr>
  </table>
</body>
</html>